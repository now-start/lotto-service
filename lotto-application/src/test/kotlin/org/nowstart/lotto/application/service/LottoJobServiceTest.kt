package org.nowstart.lotto.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.util.concurrent.Delayed
import java.util.concurrent.Executor
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.nowstart.lotto.application.port.inbound.JobState
import org.nowstart.lotto.application.port.inbound.TargetCommand
import org.nowstart.lotto.application.port.outbound.CheckResult
import org.nowstart.lotto.application.port.outbound.LoadLottoUsersPort
import org.nowstart.lotto.application.port.outbound.LottoAccountSnapshot
import org.nowstart.lotto.application.port.outbound.LottoResult
import org.nowstart.lotto.application.port.outbound.LottoUser
import org.nowstart.lotto.domain.calendar.LottoDrawCalendar
import org.nowstart.lotto.domain.exception.LoginRejectedException
import org.nowstart.lotto.domain.exception.LottoAutomationException
import org.nowstart.lotto.domain.ledger.PurchasePlan
import org.nowstart.lotto.domain.type.StepType
import org.nowstart.lotto.domain.type.TaskMode
import org.nowstart.lotto.domain.type.TriggerType
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.Trigger

class LottoJobServiceTest {

    private val clock = MutableClock(MONDAY_NOON)
    private val scheduler = FakeTaskScheduler()
    private val usersPort = mockk<LoadLottoUsersPort>()
    private val attemptRunner = mockk<LottoAttemptRunner>()
    private val notifier = mockk<JobNotifier>(relaxed = true)
    private val user = LottoUser("user1", "password", 5, "user1@nowstart.org", false)
    private val account = LottoAccountSnapshot("홍길동", "60,000원")
    private val service =
        LottoJobService(usersPort, attemptRunner, notifier, LottoDrawCalendar(clock), scheduler, Executor { it.run() }, AccountLocks())

    init {
        every { usersPort.loadUsers() } returns listOf(user)
    }

    @Test
    fun `구매 작업은 즉시 반환되고 백그라운드에서 성공하면 한 번만 통지한다`() {
        val purchased = PurchaseOutcome.Purchased(PurchasePlan.of(1252, 5, 0), emptyList())
        every { attemptRunner.run(user, TaskMode.PURCHASE, any()) } returns AttemptResult.Purchase(purchased, account, null)

        val result = service.start(TaskMode.PURCHASE, TargetCommand.all(TriggerType.MANUAL))
        val executionId = requireNotNull(result.execution).id
        assertThat(result.execution?.jobs?.single()?.state).isEqualTo(JobState.QUEUED)

        runDue()

        val job = service.find(executionId)?.jobs?.single()
        assertThat(job?.state).isEqualTo(JobState.SUCCEEDED)
        assertThat(job?.attempts).isEqualTo(1)
        assertThat(job?.round).isEqualTo(1252)
        verify(exactly = 1) { notifier.purchase(user, account, purchased, null) }
    }

    @Test
    fun `사이트 장애로 실패하면 5분 10분 간격으로 다시 시도하고 최종 결과만 통지한다`() {
        val purchased = PurchaseOutcome.Purchased(PurchasePlan.of(1252, 5, 0), emptyList())
        every { attemptRunner.run(user, TaskMode.PURCHASE, any()) } returnsMany listOf(siteDown(), siteDown(), AttemptResult.Purchase(purchased, account, null))

        val executionId = requireNotNull(service.start(TaskMode.PURCHASE, TargetCommand.all(TriggerType.SCHEDULE)).execution).id
        runDue()
        assertThat(scheduler.earliest()).isEqualTo(MONDAY_NOON.plus(Duration.ofMinutes(5)))
        advanceToNextAttempt()
        assertThat(scheduler.earliest()).isEqualTo(MONDAY_NOON.plus(Duration.ofMinutes(15)))
        advanceToNextAttempt()

        assertThat(service.find(executionId)?.jobs?.single()?.attempts).isEqualTo(3)
        verify(exactly = 0) { notifier.failure(any(), any(), any(), any()) }
        verify(exactly = 1) { notifier.purchase(user, account, purchased, null) }
    }

    @Test
    fun `같은 계정의 같은 작업이 진행 중이면 새로 만들지 않고 기존 작업을 알려 준다`() {
        every { attemptRunner.run(user, TaskMode.PURCHASE, any()) } returns siteDown()
        val first = requireNotNull(service.start(TaskMode.PURCHASE, TargetCommand.all(TriggerType.MANUAL)).execution)
        runDue()

        val second = service.start(TaskMode.PURCHASE, TargetCommand.all(TriggerType.MANUAL))

        assertThat(second.execution).isNull()
        assertThat(second.alreadyActive.single().executionId).isEqualTo(first.id)
        assertThat(second.alreadyActive.single().state).isEqualTo(JobState.RETRY_SCHEDULED)
    }

    @Test
    fun `구매 여부를 확인하지 못하면 원장 반영을 기다렸다가 다시 확인한다`() {
        val plan = PurchasePlan.of(1252, 5, 0)
        val recheckAt = MONDAY_NOON.plus(Duration.ofMinutes(10))
        val already = PurchaseOutcome.AlreadyPurchased(PurchasePlan.of(1252, 5, 5), emptyList())
        every { attemptRunner.run(user, TaskMode.PURCHASE, any()) } returnsMany listOf(
            AttemptResult.Purchase(PurchaseOutcome.Uncertain(plan, recheckAt), account, null),
            AttemptResult.Purchase(already, account, null),
        )

        val executionId = requireNotNull(service.start(TaskMode.PURCHASE, TargetCommand.all(TriggerType.MANUAL)).execution).id
        runDue()
        assertThat(service.find(executionId)?.jobs?.single()?.state).isEqualTo(JobState.UNCERTAIN)
        assertThat(scheduler.earliest()).isEqualTo(recheckAt)

        advanceToNextAttempt()

        assertThat(service.find(executionId)?.jobs?.single()?.state).isEqualTo(JobState.ALREADY_PURCHASED)
        verify(exactly = 1) { notifier.purchase(any(), any(), any(), any()) }
        verify { notifier.purchase(user, account, already, null) }
    }

    @Test
    fun `판매 시간 밖이면 다음 판매 시작으로 미루고 시도 횟수에 넣지 않는다`() {
        val next = ZonedDateTime.parse("2026-11-24T06:00:00+09:00[Asia/Seoul]")
        every { attemptRunner.run(user, TaskMode.PURCHASE, any()) } returns
            AttemptResult.Purchase(PurchaseOutcome.NotOnSale(next, 1252), null, null)

        val executionId = requireNotNull(service.start(TaskMode.PURCHASE, TargetCommand.all(TriggerType.MANUAL)).execution).id
        runDue()

        val job = service.find(executionId)?.jobs?.single()
        assertThat(job?.state).isEqualTo(JobState.DEFERRED)
        assertThat(job?.attempts).isZero()
        assertThat(job?.nextAttemptAt).isBetween(next.toInstant(), next.toInstant().plusSeconds(60))
        verify(exactly = 0) { notifier.purchase(any(), any(), any(), any()) }
    }

    @Test
    fun `로그인이 2회 연속 거부되면 계정 잠김을 막기 위해 멈추고 실패를 알린다`() {
        every { attemptRunner.run(user, TaskMode.PURCHASE, any()) } returns AttemptResult.Failed(
            LottoAutomationException(StepType.LOGIN, user.id, LoginRejectedException("rejected")), StepType.LOGIN, null,
        )

        val executionId = requireNotNull(service.start(TaskMode.PURCHASE, TargetCommand.all(TriggerType.MANUAL)).execution).id
        runDue()
        advanceToNextAttempt()

        val job = service.find(executionId)?.jobs?.single()
        assertThat(job?.state).isEqualTo(JobState.FAILED)
        assertThat(job?.attempts).isEqualTo(2)
        assertThat(scheduler.earliest()).isNull()
        verify(exactly = 1) { notifier.failure(user, TaskMode.PURCHASE, any(), any()) }
    }

    @Test
    fun `재시도 상한에 닿으면 실패로 끝내고 한 번만 알린다`() {
        every { attemptRunner.run(user, TaskMode.CHECK, any()) } returns siteDown()

        val executionId = requireNotNull(service.start(TaskMode.CHECK, TargetCommand.all(TriggerType.SCHEDULE)).execution).id
        runDue()
        while (scheduler.earliest() != null) {
            advanceToNextAttempt()
        }

        val job = service.find(executionId)?.jobs?.single()
        assertThat(job?.state).isEqualTo(JobState.FAILED)
        assertThat(job?.attempts).isEqualTo(LottoJobService.MAX_ATTEMPTS)
        verify(exactly = 1) { notifier.failure(user, TaskMode.CHECK, any(), any()) }
    }

    @Test
    fun `결과가 아직 반영되지 않았으면 다시 확인하고 반영되면 알린다`() {
        clock.set(ZonedDateTime.parse("2026-11-28T22:00:00+09:00[Asia/Seoul]").toInstant())
        val waiting = CheckResult(LottoResult("2026-11-22", "1252", "로또6/45", "A", "5", "미추첨", "5,000"), null)
        val drawn = CheckResult(LottoResult("2026-11-22", "1252", "로또6/45", "A", "5", "낙첨", "5,000"), null)
        every { attemptRunner.run(user, TaskMode.CHECK, any()) } returnsMany listOf(
            AttemptResult.Checked(listOf(waiting), account),
            AttemptResult.Checked(listOf(drawn), account),
        )

        val executionId = requireNotNull(service.start(TaskMode.CHECK, TargetCommand.all(TriggerType.SCHEDULE)).execution).id
        runDue()
        assertThat(service.find(executionId)?.jobs?.single()?.state).isEqualTo(JobState.RETRY_SCHEDULED)
        advanceToNextAttempt()

        assertThat(service.find(executionId)?.jobs?.single()?.state).isEqualTo(JobState.SUCCEEDED)
        verify(exactly = 1) { notifier.check(user, account, listOf(drawn)) }
    }

    @Test
    fun `종료 중이면 예약된 시도를 실행하지 않고 중단으로 끝낸다`() {
        every { attemptRunner.run(user, TaskMode.PURCHASE, any()) } returns siteDown()
        val executionId = requireNotNull(service.start(TaskMode.PURCHASE, TargetCommand.all(TriggerType.MANUAL)).execution).id
        runDue()

        service.stop()
        advanceToNextAttempt()

        assertThat(service.find(executionId)?.jobs?.single()?.state).isEqualTo(JobState.ABORTED)
        verify(exactly = 1) { attemptRunner.run(any(), any(), any()) }
    }

    @Test
    fun `재시도 간격은 5분에서 두 배씩 늘어 최대 60분이다`() {
        assertThat((1..6).map { LottoJobService.backoff(it).toMinutes() }).containsExactly(5, 10, 20, 40, 60, 60)
    }

    private fun siteDown() = AttemptResult.Failed(
        LottoAutomationException(StepType.LOGIN, user.id, IllegalStateException("Timeout 30000ms exceeded")), StepType.LOGIN, null,
    )

    private fun runDue() {
        while (true) {
            val task = scheduler.pollDue(clock.instant()) ?: return
            task.run()
        }
    }

    private fun advanceToNextAttempt() {
        clock.set(requireNotNull(scheduler.earliest()) { "no scheduled attempt" })
        runDue()
    }

    private companion object {
        /** 2026-11-23(월) 12:00 KST: 판매 회차 1252. */
        val MONDAY_NOON: Instant = ZonedDateTime.parse("2026-11-23T12:00:00+09:00[Asia/Seoul]").toInstant()
    }
}

/** 예약만 기록하고 테스트가 시간을 움직여 실행하는 스케줄러. 취소된 예약(감시 타이머)은 버린다. */
class FakeTaskScheduler : TaskScheduler {

    private val tasks = mutableListOf<ScheduledTask>()

    fun earliest(): Instant? = tasks.minOfOrNull { it.at }

    fun pollDue(now: Instant): Runnable? {
        val due = tasks.filter { !it.at.isAfter(now) }.minByOrNull { it.at } ?: return null
        tasks.remove(due)
        return due.task
    }

    override fun schedule(task: Runnable, startTime: Instant): ScheduledFuture<*> =
        ScheduledTask(task, startTime) { tasks.remove(it) }.also { tasks += it }

    override fun schedule(task: Runnable, trigger: Trigger): ScheduledFuture<*>? = unsupported()

    override fun scheduleAtFixedRate(task: Runnable, startTime: Instant, period: Duration): ScheduledFuture<*> = unsupported()

    override fun scheduleAtFixedRate(task: Runnable, period: Duration): ScheduledFuture<*> = unsupported()

    override fun scheduleWithFixedDelay(task: Runnable, startTime: Instant, delay: Duration): ScheduledFuture<*> = unsupported()

    override fun scheduleWithFixedDelay(task: Runnable, delay: Duration): ScheduledFuture<*> = unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOperationException("not used")

    class ScheduledTask(val task: Runnable, val at: Instant, private val onCancel: (ScheduledTask) -> Unit) : ScheduledFuture<Any?> {
        private var cancelled = false

        override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
            cancelled = true
            onCancel(this)
            return true
        }

        override fun isCancelled(): Boolean = cancelled

        override fun isDone(): Boolean = cancelled

        override fun get(): Any? = null

        override fun get(timeout: Long, unit: TimeUnit): Any? = null

        override fun getDelay(unit: TimeUnit): Long = 0

        override fun compareTo(other: Delayed): Int = 0
    }
}
