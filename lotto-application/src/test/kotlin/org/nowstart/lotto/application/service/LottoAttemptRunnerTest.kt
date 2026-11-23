package org.nowstart.lotto.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicBoolean
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.nowstart.lotto.application.port.outbound.Attachment
import org.nowstart.lotto.application.port.outbound.CheckResult
import org.nowstart.lotto.application.port.outbound.LottoAccountSnapshot
import org.nowstart.lotto.application.port.outbound.LottoAutomationPort
import org.nowstart.lotto.application.port.outbound.LottoResult
import org.nowstart.lotto.application.port.outbound.LottoUser
import org.nowstart.lotto.domain.ledger.PurchasePlan
import org.nowstart.lotto.domain.type.StepType
import org.nowstart.lotto.domain.type.TaskMode

class LottoAttemptRunnerTest {

    private val port = mockk<LottoAutomationPort>()
    private val procedure = mockk<LottoPurchaseProcedure>()
    private val runner = LottoAttemptRunner(port, procedure)
    private val user = LottoUser("user1", "password", 1, "user1@nowstart.org", false)
    private val account = LottoAccountSnapshot("ok", "5000")

    @Test
    fun `결과 확인은 구매 없이 원장 결과를 돌려준다`() {
        val session = FakeSession()
        val results = listOf(CheckResult(LottoResult("2026-01-01", "1000", "로또", "1,2,3,4,5,6", "1", "당첨", "5000"), byteArrayOf(1)))
        every { port.openSession() } returns session
        every { port.login(session, user) } returns account
        every { port.check(session) } returns results

        val result = runner.run(user, TaskMode.CHECK, AtomicBoolean(false))

        assertThat(result).isEqualTo(AttemptResult.Checked(results, account))
        assertThat(session.closed).isTrue()
        verify(exactly = 0) { procedure.precheck(any()) }
    }

    @Test
    fun `판매 시간 밖이면 브라우저를 열지 않는다`() {
        val notOnSale = PurchaseOutcome.NotOnSale(ZonedDateTime.parse("2026-11-29T06:00+09:00[Asia/Seoul]"), 1253)
        every { procedure.precheck(user) } returns notOnSale

        assertThat(runner.run(user, TaskMode.PURCHASE, AtomicBoolean(false))).isEqualTo(AttemptResult.Purchase(notOnSale, null, null))
        verify(exactly = 0) { port.openSession() }
    }

    @Test
    fun `구매 판정 불가면 세션을 실패로 표시해 진단 자료를 남긴다`() {
        val trace = Attachment("lotto-trace.zip", byteArrayOf(9))
        val session = FakeSession(traceOnClose = trace)
        val uncertain = PurchaseOutcome.Uncertain(PurchasePlan.of(1252, 1, 0), Instant.parse("2026-11-23T02:40:00Z"))
        every { procedure.precheck(user) } returns null
        every { port.openSession() } returns session
        every { port.login(session, user) } returns account
        every { procedure.purchase(session, user, account, any()) } returns uncertain

        val result = runner.run(user, TaskMode.PURCHASE, AtomicBoolean(false))

        assertThat(result).isEqualTo(AttemptResult.Purchase(uncertain, account, trace))
        assertThat(session.failed).isTrue()
    }

    @Test
    fun `단계 실패는 예외 대신 실패 결과로 돌려주고 닫힌 세션의 진단 자료를 붙인다`() {
        val trace = Attachment("lotto-trace-user1.zip", byteArrayOf(1, 2, 3))
        val session = FakeSession(traceOnClose = trace)
        every { procedure.precheck(user) } returns null
        every { port.openSession() } returns session
        every { port.login(session, user) } throws IllegalStateException("login failed")

        val result = runner.run(user, TaskMode.PURCHASE, AtomicBoolean(false))

        assertThat(result).isInstanceOfSatisfying(AttemptResult.Failed::class.java) {
            assertThat(it.step).isEqualTo(StepType.LOGIN)
            assertThat(it.trace).isSameAs(trace)
        }
        assertThat(session.events).containsExactly("markFailed", "close")
    }
}
