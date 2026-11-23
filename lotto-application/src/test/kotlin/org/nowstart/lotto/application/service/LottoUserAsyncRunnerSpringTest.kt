package org.nowstart.lotto.application.service

import java.time.Clock
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.nowstart.lotto.application.port.outbound.CheckResult
import org.nowstart.lotto.application.port.outbound.Delivery
import org.nowstart.lotto.application.port.outbound.LottoAccountSnapshot
import org.nowstart.lotto.application.port.outbound.LottoAutomationPort
import org.nowstart.lotto.application.port.outbound.LottoAutomationSession
import org.nowstart.lotto.application.port.outbound.LottoResult
import org.nowstart.lotto.application.port.outbound.LottoUser
import org.nowstart.lotto.application.port.outbound.PurchaseSubmission
import org.nowstart.lotto.application.port.outbound.SendNotificationPort
import org.nowstart.lotto.domain.calendar.LottoDrawCalendar
import org.nowstart.lotto.domain.type.TaskMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig

@SpringJUnitConfig(
    classes = [
        LottoUserAsyncRunner::class,
        LottoNotificationFactory::class,
        LottoPurchaseProcedure::class,
        UncertainPurchaseRegistry::class,
        LottoUserAsyncRunnerSpringTest.Config::class,
    ],
)
class LottoUserAsyncRunnerSpringTest {

    @Autowired
    private lateinit var lottoUserRunner: LottoUserRunner

    @Autowired
    private lateinit var blockingPort: BlockingLottoAutomationPort

    @Test
    fun `Spring 프록시를 통해 사용자 작업을 비동기로 실행한다`() {
        val user = LottoUser("user1", "password", 1, "user1@nowstart.org", false)

        val future = lottoUserRunner.runAsync(user, TaskMode.CHECK, AtomicBoolean(false))

        assertThat(blockingPort.loginStarted.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(future.isDone).isFalse()
        assertThat(blockingPort.loginThread.get()).isNotSameAs(Thread.currentThread())

        blockingPort.releaseLogin.countDown()
        assertThat(future.get(5, TimeUnit.SECONDS)).isTrue()
    }

    @Configuration
    @EnableAsync
    class Config {

        @Bean(name = ["lottoTaskExecutor"], destroyMethod = "shutdownNow")
        fun lottoTaskExecutor(): ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

        @Bean
        fun blockingLottoAutomationPort() = BlockingLottoAutomationPort()

        @Bean
        fun sendNotificationPort() = SendNotificationPort { Delivery.SENT }

        @Bean
        fun clock(): Clock = Clock.system(LottoDrawCalendar.ZONE)

        @Bean
        fun lottoDrawCalendar(clock: Clock) = LottoDrawCalendar(clock)
    }

    class BlockingLottoAutomationPort : LottoAutomationPort {

        val loginStarted = CountDownLatch(1)
        val releaseLogin = CountDownLatch(1)
        val loginThread = AtomicReference<Thread>()

        override fun openSession(): LottoAutomationSession = object : LottoAutomationSession {
            override fun close() {}
        }

        override fun login(session: LottoAutomationSession, user: LottoUser): LottoAccountSnapshot {
            loginThread.set(Thread.currentThread())
            loginStarted.countDown()
            check(releaseLogin.await(5, TimeUnit.SECONDS)) { "login was not released" }
            return LottoAccountSnapshot("ok", "1000")
        }

        override fun check(session: LottoAutomationSession): List<CheckResult> = listOf(
            CheckResult(LottoResult("2026-01-01", "1000", "로또", "1,2,3,4,5,6", "1", "당첨", "5000"), byteArrayOf(1)),
        )

        override fun readAccount(session: LottoAutomationSession) = unused()

        override fun readLedger(session: LottoAutomationSession) = unused()

        override fun awaitLedger(
            session: LottoAutomationSession,
            condition: (List<LottoResult>) -> Boolean,
            timeout: Duration,
            abortRequested: () -> Boolean,
        ) = unused()

        override fun captureTickets(session: LottoAutomationSession, rows: List<LottoResult>) = unused()

        override fun buy(session: LottoAutomationSession, user: LottoUser, count: Int, abortRequested: () -> Boolean): PurchaseSubmission =
            unused()

        private fun unused(): Nothing = throw UnsupportedOperationException("not used in this test")
    }
}
