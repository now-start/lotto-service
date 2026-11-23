package org.nowstart.lotto.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import java.time.Instant
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicBoolean
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.nowstart.lotto.application.port.outbound.Attachment
import org.nowstart.lotto.application.port.outbound.CheckResult
import org.nowstart.lotto.application.port.outbound.Delivery
import org.nowstart.lotto.application.port.outbound.LottoAccountSnapshot
import org.nowstart.lotto.application.port.outbound.LottoAutomationPort
import org.nowstart.lotto.application.port.outbound.LottoResult
import org.nowstart.lotto.application.port.outbound.LottoUser
import org.nowstart.lotto.application.port.outbound.NotificationMessage
import org.nowstart.lotto.application.port.outbound.SendNotificationPort
import org.nowstart.lotto.domain.ledger.PurchasePlan
import org.nowstart.lotto.domain.type.TaskMode
import org.springframework.scheduling.annotation.Async

class LottoUserAsyncRunnerTest {

    private val port = mockk<LottoAutomationPort>()
    private val sendPort = mockk<SendNotificationPort>()
    private val factory = mockk<LottoNotificationFactory>()
    private val procedure = mockk<LottoPurchaseProcedure>()
    private val runner = LottoUserAsyncRunner(port, sendPort, factory, procedure)
    private val user = LottoUser("user1", "password", 1, "user1@nowstart.org", false)
    private val account = LottoAccountSnapshot("ok", "5000")

    @Test
    fun `사용자 작업 실행 메서드는 lottoTaskExecutor 기반 Async로 선언한다`() {
        val runAsync = LottoUserAsyncRunner::class.java
            .getMethod("runAsync", LottoUser::class.java, TaskMode::class.java, AtomicBoolean::class.java)

        assertThat(runAsync.getAnnotation(Async::class.java)?.value).isEqualTo("lottoTaskExecutor")
    }

    @Test
    fun `일반 확인이면 구매하지 않고 확인 결과 알림을 보낸다`() {
        val session = FakeSession()
        val message = message("subject")
        every { port.openSession() } returns session
        every { port.login(session, user) } returns account
        every { port.check(session) } returns listOf(checkResult())
        every { factory.createCheckSuccessMessage(user, account, any()) } returns message
        every { sendPort.send(message) } returns Delivery.SENT

        assertThat(runner.runAsync(user, TaskMode.CHECK, AtomicBoolean(false)).join()).isTrue()

        verify(exactly = 0) { procedure.precheck(any()) }
        verify(exactly = 1) { sendPort.send(message) }
    }

    @Test
    fun `구매이면 로그인 후 구매 절차를 실행하고 결과 통지를 보낸다`() {
        val session = FakeSession()
        val outcome = PurchaseOutcome.Purchased(PurchasePlan.of(1252, 1, 0), listOf(checkResult()))
        val success = message("success")
        every { procedure.precheck(user) } returns null
        every { port.openSession() } returns session
        every { port.login(session, user) } returns account
        every { procedure.purchase(session, user, account, any()) } returns outcome
        every { factory.createPurchaseOutcomeMessage(user, account, outcome) } returns success
        every { sendPort.send(success) } returns Delivery.SENT

        assertThat(runner.runAsync(user, TaskMode.PURCHASE, AtomicBoolean(false)).join()).isTrue()

        verifyOrder {
            port.login(session, user)
            procedure.purchase(session, user, account, any())
        }
        verify(exactly = 0) { port.check(any()) }
        assertThat(session.failed).isFalse()
    }

    @Test
    fun `판매 시간이 아니면 브라우저를 열지 않고 안내만 보낸다`() {
        val notOnSale = PurchaseOutcome.NotOnSale(ZonedDateTime.parse("2026-11-29T06:00+09:00[Asia/Seoul]"), 1253)
        val notice = message("notice")
        every { procedure.precheck(user) } returns notOnSale
        every { factory.createPurchaseOutcomeMessage(user, null, notOnSale) } returns notice
        every { sendPort.send(notice) } returns Delivery.SENT

        assertThat(runner.runAsync(user, TaskMode.PURCHASE, AtomicBoolean(false)).join()).isFalse()

        verify(exactly = 0) { port.openSession() }
        verify { sendPort.send(notice) }
    }

    @Test
    fun `구매 판정 불가면 진단 자료를 남기고 수동 구매 금지 안내를 보낸다`() {
        val trace = Attachment("lotto-trace.zip", byteArrayOf(9))
        val session = FakeSession(traceOnClose = trace)
        val uncertain = PurchaseOutcome.Uncertain(PurchasePlan.of(1252, 1, 0), Instant.parse("2026-11-23T02:40:00Z"))
        val sent = slot<NotificationMessage>()
        every { procedure.precheck(user) } returns null
        every { port.openSession() } returns session
        every { port.login(session, user) } returns account
        every { procedure.purchase(session, user, account, any()) } returns uncertain
        every { factory.createPurchaseOutcomeMessage(user, account, uncertain) } returns message("uncertain")
        every { sendPort.send(capture(sent)) } returns Delivery.SENT

        assertThat(runner.runAsync(user, TaskMode.PURCHASE, AtomicBoolean(false)).join()).isFalse()

        assertThat(session.failed).isTrue()
        assertThat(sent.captured.attachment).isSameAs(trace)
    }

    @Test
    fun `사용자 작업 실패는 세션을 닫은 뒤 진단 자료를 붙여 실패 알림을 보낸다`() {
        val trace = Attachment("lotto-trace-user1.zip", byteArrayOf(1, 2, 3))
        val session = FakeSession(traceOnClose = trace)
        val sent = slot<NotificationMessage>()
        every { procedure.precheck(user) } returns null
        every { port.openSession() } returns session
        every { port.login(session, user) } throws IllegalStateException("login failed")
        every { factory.createFailureMessage(user, TaskMode.PURCHASE, any()) } returns message("failure")
        every { sendPort.send(capture(sent)) } answers {
            assertThat(session.closed).isTrue()
            Delivery.SENT
        }

        assertThat(runner.runAsync(user, TaskMode.PURCHASE, AtomicBoolean(false)).join()).isFalse()

        assertThat(session.events).containsExactly("markFailed", "close")
        assertThat(sent.captured.attachment?.content).containsExactly(1, 2, 3)
    }

    @Test
    fun `치명적 오류는 사용자 실패로 숨기지 않는다`() {
        val fatal = OutOfMemoryError("browser launch failed")
        every { port.openSession() } throws fatal

        assertThatThrownBy { runner.runAsync(user, TaskMode.CHECK, AtomicBoolean(false)).join() }.isSameAs(fatal)
    }

    private fun message(subject: String) = NotificationMessage(subject, "text", null, user.email)

    private fun checkResult() = CheckResult(
        LottoResult("2026-01-01", "1000", "로또", "1,2,3,4,5,6", "1", "당첨", "5000"),
        byteArrayOf(1),
    )
}
