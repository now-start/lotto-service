package org.nowstart.lotto.application.service

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.UnsynchronizedAppenderBase
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Delayed
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.nowstart.lotto.application.port.inbound.JobState
import org.nowstart.lotto.application.port.inbound.TargetCommand
import org.nowstart.lotto.application.port.outbound.CheckResult
import org.nowstart.lotto.application.port.outbound.LoadUsersPort
import org.nowstart.lotto.application.port.outbound.AccountSnapshot
import org.nowstart.lotto.domain.ledger.TicketResult
import org.nowstart.lotto.application.port.outbound.UserAccount
import org.nowstart.lotto.domain.calendar.DrawCalendar
import org.nowstart.lotto.domain.exception.LoginRejectedException
import org.nowstart.lotto.domain.exception.AutomationException
import org.nowstart.lotto.domain.ledger.PurchasePlan
import org.nowstart.lotto.domain.type.StepType
import org.nowstart.lotto.domain.type.TaskMode
import org.nowstart.lotto.domain.type.TriggerType
import org.slf4j.LoggerFactory
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.Trigger

class JobServiceTest {

    private val clock = MutableClock(MONDAY_NOON)
    private val scheduler = FakeTaskScheduler()
    private val usersPort = mockk<LoadUsersPort>()
    private val attemptRunner = mockk<AttemptRunner>()
    private val notifier = mockk<JobNotifier>(relaxed = true)
    private val user = UserAccount("user1", "password", 5, "user1@nowstart.org", false)
    private val account = AccountSnapshot("홍길동", "60,000원")
    private val locks = AccountLocks()
    private val service = newService()

    private fun newService(taskScheduler: TaskScheduler = scheduler, executor: Executor = Executor { it.run() }) =
        JobService(usersPort, attemptRunner, notifier, DrawCalendar(clock), taskScheduler, executor, locks)

    init {
        every { usersPort.loadUsers() } returns listOf(user)
    }

    @Test
    fun `구매 작업은 즉시 반환되고 백그라운드에서 성공하면 한 번만 통지한다`() {
        val purchased = PurchaseOutcome.Purchased(PurchasePlan.of(1252, 5, 0), emptyList())
        every { attemptRunner.run(user, TaskMode.PURCHASE, any(), 1252) } returns AttemptResult.Purchase(purchased, account, null)

        val result = service.start(TaskMode.PURCHASE, TargetCommand.all(TriggerType.MANUAL))
        val execution = requireNotNull(result.execution)
        assertThat(execution.jobs.single().state).isEqualTo(JobState.QUEUED)

        runDue()

        val job = service.find(execution.id)?.jobs?.single()
        assertThat(job?.state).isEqualTo(JobState.SUCCEEDED)
        assertThat(job?.attempts).isEqualTo(1)
        assertThat(job?.round).isEqualTo(1252)
        verify(exactly = 1) { notifier.purchase(user, account, purchased, null) }
    }

    @Test
    fun `사이트 장애로 실패하면 5분 10분 간격으로 다시 시도하고 최종 결과만 통지한다`() {
        val purchased = PurchaseOutcome.Purchased(PurchasePlan.of(1252, 5, 0), emptyList())
        every { attemptRunner.run(user, TaskMode.PURCHASE, any(), 1252) } returnsMany listOf(siteDown(), siteDown(), AttemptResult.Purchase(purchased, account, null))

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
        every { attemptRunner.run(user, TaskMode.PURCHASE, any(), 1252) } returns siteDown()
        val first = requireNotNull(service.start(TaskMode.PURCHASE, TargetCommand.all(TriggerType.MANUAL)).execution)
        runDue()

        val second = service.start(TaskMode.PURCHASE, TargetCommand.all(TriggerType.MANUAL))

        assertThat(second.execution).isNull()
        assertThat(second.alreadyActive.single().executionId).isEqualTo(first.id)
        assertThat(second.alreadyActive.single().state).isEqualTo(JobState.RETRY_SCHEDULED)
    }

    @Test
    fun `구매 중인 계정의 조회만 대기하고 다른 계정은 바로 조회한다`() {
        val other = user.copy(id = "user2")
        every { usersPort.loadUsers() } returns listOf(user, other)
        val purchaseStarted = CountDownLatch(1)
        val finishPurchase = CountDownLatch(1)
        every { attemptRunner.run(user, TaskMode.PURCHASE, any(), 1252) } answers {
            purchaseStarted.countDown()
            check(finishPurchase.await(10, TimeUnit.SECONDS))
            AttemptResult.Purchase(PurchaseOutcome.Purchased(PurchasePlan.of(1252, 5, 0), emptyList()), account, null)
        }
        every { attemptRunner.run(any(), TaskMode.CHECK, any(), null) } returns AttemptResult.Checked(emptyList(), account)

        val purchase = requireNotNull(service.start(TaskMode.PURCHASE, TargetCommand(TriggerType.MANUAL, listOf(user.id))).execution)
        val purchaseAttempt = requireNotNull(scheduler.pollDue(clock.instant()))
        val checks = requireNotNull(service.start(TaskMode.CHECK, TargetCommand.all(TriggerType.SCHEDULE)).execution)
        val inProgress = CompletableFuture.runAsync(purchaseAttempt)
        try {
            assertThat(purchaseStarted.await(5, TimeUnit.SECONDS)).isTrue()
            runDue()

            val jobs = requireNotNull(service.find(checks.id)).jobs.associateBy { it.userId }
            assertThat(jobs.getValue(other.id).state).isEqualTo(JobState.SUCCEEDED)
            assertThat(jobs.getValue(user.id).attempts).isZero()
            assertThat(jobs.getValue(user.id).nextAttemptAt).isEqualTo(MONDAY_NOON.plus(JobService.ACCOUNT_BUSY_DELAY))
            assertThat(requireNotNull(service.find(purchase.id)).jobs.single().state).isEqualTo(JobState.RUNNING)
        } finally {
            finishPurchase.countDown()
            inProgress.get(5, TimeUnit.SECONDS)
        }

        advanceToNextAttempt()
        assertThat(requireNotNull(service.find(checks.id)).jobs.map { it.state }).containsOnly(JobState.SUCCEEDED)
        verify(exactly = 1) { attemptRunner.run(user, TaskMode.CHECK, any(), null) }
        verify(exactly = 1) { attemptRunner.run(other, TaskMode.CHECK, any(), null) }
    }

    @Test
    fun `중복 요청이 받은 실행 ID로 두 계정의 작업을 즉시 조회할 수 있다`() {
        val other = user.copy(id = "user2")
        every { usersPort.loadUsers() } returns listOf(user, other)
        // 이전 구현에서는 시작 로그가 작업 등록 잠금 해제와 실행 이력 공개 사이에 있었다.
        val startLogged = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val appender = object : UnsynchronizedAppenderBase<ILoggingEvent>() {
            override fun append(event: ILoggingEvent) {
                if (event.argumentArray?.contains(TriggerType.SCHEDULE) == true) {
                    startLogged.countDown()
                    resume.await()
                }
            }
        }
        val logger = LoggerFactory.getLogger(JobService::class.java) as Logger
        val previousLevel = logger.level
        logger.level = Level.INFO
        appender.start()
        logger.addAppender(appender)
        try {
            val first = CompletableFuture.supplyAsync { service.start(TaskMode.CHECK, TargetCommand.all(TriggerType.SCHEDULE)) }
            assertThat(startLogged.await(5, TimeUnit.SECONDS)).isTrue()
            val duplicate = CompletableFuture.supplyAsync {
                service.start(TaskMode.CHECK, TargetCommand(TriggerType.MANUAL, listOf(other.id)))
            }.get(5, TimeUnit.SECONDS)
            assertThat(duplicate.execution).isNull()
            val executionId = duplicate.alreadyActive.single().executionId
            assertThat(service.find(executionId)?.jobs?.map { it.userId }).containsExactly(user.id, other.id)
            resume.countDown()
            assertThat(first.get(5, TimeUnit.SECONDS).execution?.id).isEqualTo(executionId)
        } finally {
            resume.countDown()
            logger.detachAppender(appender)
            appender.stop()
            logger.level = previousLevel
        }
    }

    @Test
    fun `구매 여부를 확인하지 못하면 원장 반영을 기다렸다가 다시 확인한다`() {
        val plan = PurchasePlan.of(1252, 5, 0)
        val recheckAt = MONDAY_NOON.plus(Duration.ofMinutes(10))
        val already = PurchaseOutcome.AlreadyPurchased(PurchasePlan.of(1252, 5, 5), emptyList())
        every { attemptRunner.run(user, TaskMode.PURCHASE, any(), 1252) } returnsMany listOf(
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
        every { attemptRunner.run(user, TaskMode.PURCHASE, any(), 1252) } returns
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
        every { attemptRunner.run(user, TaskMode.PURCHASE, any(), 1252) } returns AttemptResult.Failed(
            AutomationException(StepType.LOGIN, user.id, LoginRejectedException("rejected")), StepType.LOGIN, null,
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
    fun `로그인 전에 원장 반영을 기다리면 연속 로그인 거부 횟수를 유지한다`() {
        val rejected = AttemptResult.Failed(
            AutomationException(StepType.LOGIN, user.id, LoginRejectedException("rejected")), StepType.LOGIN, null,
        )
        val blocked = PurchaseOutcome.Blocked(1252, MONDAY_NOON.plus(Duration.ofMinutes(10)))
        every { attemptRunner.run(user, TaskMode.PURCHASE, any(), 1252) } returnsMany listOf(
            rejected, AttemptResult.Purchase(blocked, null, null), rejected,
        )
        val execution = requireNotNull(service.start(TaskMode.PURCHASE, TargetCommand.all(TriggerType.MANUAL)).execution)
        runDue()
        advanceToNextAttempt()
        advanceToNextAttempt()

        val job = requireNotNull(service.find(execution.id)).jobs.single()
        assertThat(job.state).isEqualTo(JobState.FAILED)
        assertThat(job.attempts).isEqualTo(2)
        assertThat(scheduler.earliest()).isNull()
        verify(exactly = 1) { notifier.failure(user, TaskMode.PURCHASE, any(), null) }
    }

    @Test
    fun `재시도 상한에 닿으면 실패로 끝내고 한 번만 알린다`() {
        every { attemptRunner.run(user, TaskMode.CHECK, any(), null) } returns siteDown()

        val executionId = requireNotNull(service.start(TaskMode.CHECK, TargetCommand.all(TriggerType.SCHEDULE)).execution).id
        runDue()
        while (scheduler.earliest() != null) {
            advanceToNextAttempt()
        }

        val job = service.find(executionId)?.jobs?.single()
        assertThat(job?.state).isEqualTo(JobState.FAILED)
        assertThat(job?.attempts).isEqualTo(JobService.MAX_ATTEMPTS)
        verify(exactly = 1) { notifier.failure(user, TaskMode.CHECK, any(), any()) }
    }

    @Test
    fun `결과가 아직 반영되지 않았으면 다시 확인하고 반영되면 알린다`() {
        clock.set(ZonedDateTime.parse("2026-11-28T22:00:00+09:00[Asia/Seoul]").toInstant())
        val waiting = CheckResult(TicketResult("2026-11-22", "1252", "로또6/45", "A", "5", "미추첨", "5,000"), null)
        val drawn = CheckResult(TicketResult("2026-11-22", "1252", "로또6/45", "A", "5", "낙첨", "5,000"), null)
        every { attemptRunner.run(user, TaskMode.CHECK, any(), null) } returnsMany listOf(
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
        every { attemptRunner.run(user, TaskMode.PURCHASE, any(), 1252) } returns siteDown()
        val executionId = requireNotNull(service.start(TaskMode.PURCHASE, TargetCommand.all(TriggerType.MANUAL)).execution).id
        runDue()

        service.stop()
        clock.advance(Duration.ofMinutes(5))
        runDue()

        assertThat(service.find(executionId)?.jobs?.single()?.state).isEqualTo(JobState.ABORTED)
        verify(exactly = 1) { attemptRunner.run(user, TaskMode.PURCHASE, any(), 1252) }
        verify(exactly = 1) { notifier.failure(user, TaskMode.PURCHASE, any(), null) }
    }

    @Test
    fun `실행기까지 전달된 작업도 종료 후에는 시도하거나 두 번 통지하지 않는다`() {
        val queue = ArrayDeque<Runnable>()
        val subject = newService(executor = Executor { queue.addLast(it) })
        val execution = requireNotNull(subject.start(TaskMode.PURCHASE, TargetCommand.all(TriggerType.MANUAL)).execution)
        runDue()
        val waiting = queue.removeFirst()

        subject.stop()
        waiting.run()

        val job = requireNotNull(subject.find(execution.id)).jobs.single()
        assertThat(job.state).isEqualTo(JobState.ABORTED)
        assertThat(job.attempts).isZero()
        verify { attemptRunner wasNot Called }
        verify(exactly = 1) { notifier.failure(user, TaskMode.PURCHASE, any(), null) }
    }

    @Test
    fun `계정 잠금 대기로 늦어진 구매는 다음 회차에 실행하지 않는다`() {
        val deadline = ZonedDateTime.parse("2026-11-28T19:50:00+09:00[Asia/Seoul]").toInstant()
        clock.set(deadline.minusSeconds(5))
        locks.tryAcquire(user.id)
        val execution = requireNotNull(service.start(TaskMode.PURCHASE, TargetCommand.all(TriggerType.MANUAL)).execution)
        runDue()
        assertThat(scheduler.earliest()).isEqualTo(deadline)

        locks.release(user.id)
        clock.set(ZonedDateTime.parse("2026-11-29T06:00:00+09:00[Asia/Seoul]").toInstant())
        runDue()

        val job = requireNotNull(service.find(execution.id)).jobs.single()
        assertThat(job.round).isEqualTo(1252)
        assertThat(job.state).isEqualTo(JobState.FAILED)
        assertThat(job.attempts).isZero()
        assertThat(scheduler.earliest()).isNull()
        verify { attemptRunner wasNot Called }
        verify(exactly = 1) { notifier.failure(user, TaskMode.PURCHASE, any(), null) }
    }

    @Test
    fun `구매 확인을 기다리다 회차가 마감되면 원래 구매내역을 확인하도록 알린다`() {
        val uncertain = PurchaseOutcome.Uncertain(PurchasePlan.of(1252, 5, 0), MONDAY_NOON.plus(Duration.ofMinutes(10)))
        every { attemptRunner.run(user, TaskMode.PURCHASE, any(), 1252) } returns AttemptResult.Purchase(uncertain, account, null)
        val execution = requireNotNull(service.start(TaskMode.PURCHASE, TargetCommand.all(TriggerType.MANUAL)).execution)
        runDue()
        assertThat(service.find(execution.id)?.jobs?.single()?.state).isEqualTo(JobState.UNCERTAIN)

        clock.set(ZonedDateTime.parse("2026-11-29T06:00:00+09:00[Asia/Seoul]").toInstant())
        runDue()

        assertThat(service.find(execution.id)?.jobs?.single()?.state).isEqualTo(JobState.FAILED)
        verify(exactly = 1) { attemptRunner.run(user, TaskMode.PURCHASE, any(), 1252) }
        verify(exactly = 1) { notifier.failure(user, TaskMode.PURCHASE, match { it.message.orEmpty().contains("구매내역") }, null) }
    }

    @Test
    fun `예약이 거부되면 한 번만 실패를 알리고 새 요청을 받는다`() {
        val taskScheduler = object : TaskScheduler by scheduler {
            var reject = true

            override fun schedule(task: Runnable, startTime: Instant): ScheduledFuture<*> {
                if (reject) {
                    reject = false
                    throw RejectedExecutionException("scheduler rejected")
                }
                return scheduler.schedule(task, startTime)
            }
        }
        verifyDispatchRecovery(newService(taskScheduler = taskScheduler))
    }

    @Test
    fun `실행기가 작업을 거부하면 한 번만 실패를 알리고 새 요청을 받는다`() {
        var reject = true
        val executor = Executor { task ->
            if (reject) {
                reject = false
                throw RejectedExecutionException("executor rejected")
            }
            task.run()
        }
        verifyDispatchRecovery(newService(executor = executor))
    }

    private fun verifyDispatchRecovery(subject: JobService) {
        every { attemptRunner.run(user, TaskMode.CHECK, any(), null) } returns AttemptResult.Checked(emptyList(), account)
        val command = TargetCommand.all(TriggerType.MANUAL)
        val rejected = requireNotNull(subject.start(TaskMode.CHECK, command).execution)
        runDue()
        assertThat(subject.find(rejected.id)?.jobs?.single()?.state).isEqualTo(JobState.FAILED)

        val retried = requireNotNull(subject.start(TaskMode.CHECK, command).execution)
        runDue()
        assertThat(subject.find(retried.id)?.jobs?.single()?.state).isEqualTo(JobState.SUCCEEDED)
        verify(exactly = 1) { notifier.failure(user, TaskMode.CHECK, any(), null) }
        verify(exactly = 1) { attemptRunner.run(user, TaskMode.CHECK, any(), null) }
    }

    @Test
    fun `재시도 간격은 5분에서 두 배씩 늘어 최대 60분이다`() {
        assertThat((1..6).map { JobService.backoff(it).toMinutes() }).containsExactly(5, 10, 20, 40, 60, 60)
    }

    private fun siteDown() = AttemptResult.Failed(
        AutomationException(StepType.LOGIN, user.id, IllegalStateException("Timeout 30000ms exceeded")), StepType.LOGIN, null,
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
