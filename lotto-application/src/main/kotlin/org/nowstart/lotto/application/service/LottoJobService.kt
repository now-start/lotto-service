package org.nowstart.lotto.application.service

import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean
import org.nowstart.lotto.application.exception.InvalidManualUserSelectionException
import org.nowstart.lotto.application.port.inbound.ExecutionView
import org.nowstart.lotto.application.port.inbound.JobState
import org.nowstart.lotto.application.port.inbound.LottoUseCase
import org.nowstart.lotto.application.port.inbound.StartResult
import org.nowstart.lotto.application.port.inbound.TargetCommand
import org.nowstart.lotto.application.port.inbound.UserJobView
import org.nowstart.lotto.application.port.outbound.LoadLottoUsersPort
import org.nowstart.lotto.application.port.outbound.LottoUser
import org.nowstart.lotto.domain.calendar.LottoDrawCalendar
import org.nowstart.lotto.domain.exception.LoginRejectedException
import org.nowstart.lotto.domain.type.MessageType
import org.nowstart.lotto.domain.type.TaskMode
import org.nowstart.lotto.domain.type.TriggerType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.SmartLifecycle
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service

private val log = LoggerFactory.getLogger(LottoJobService::class.java)

/**
 * 계정별 작업(job)을 만들고, 사이트 장애가 지나갈 때까지 작업 단위로 다시 시도한다.
 *
 * - 한 번의 시도는 매번 새 브라우저 세션으로 처음부터(로그인 → 구매 전 원장 확인 → 구매) 수행한다.
 *   구매 전 원장 확인이 이미 산 수량을 빼므로 다시 시도해도 이중 구매가 되지 않는다.
 * - 다음 시도는 이번 시도가 끝난 뒤에만 예약한다. 감시 타이머는 중단 신호만 켠다.
 * - 같은 계정은 모드와 관계없이 브라우저를 동시에 하나만 쓴다(시도 실행 시점에 잡는 잠금).
 * - 통지는 작업의 최종 결과에서만 보낸다. 진행 상태는 실행 조회 API로 본다.
 * - 상태는 메모리에만 있다(단일 인스턴스 전제, 재시작 시 진행 중인 작업은 사라진다).
 */
@Service
class LottoJobService(
    private val loadLottoUsersPort: LoadLottoUsersPort,
    private val attemptRunner: LottoAttemptRunner,
    private val notifier: JobNotifier,
    private val calendar: LottoDrawCalendar,
    private val taskScheduler: TaskScheduler,
    @Qualifier("lottoTaskExecutor") private val executor: Executor,
    private val accountLocks: AccountLocks,
) : LottoUseCase, SmartLifecycle {

    private val executions = ConcurrentHashMap<String, Execution>()
    private val activeJobs = HashMap<JobKey, UserJob>()

    @Volatile
    private var accepting = true

    @Volatile
    private var lifecycleRunning = false

    override fun start(mode: TaskMode, command: TargetCommand): StartResult {
        val users = resolveTargetUsers(command.userIds)
        val now = calendar.now()
        val execution = Execution(UUID.randomUUID().toString(), mode, command.trigger, now.toInstant())
        val alreadyActive = mutableListOf<UserJob>()
        val created = mutableListOf<UserJob>()
        synchronized(activeJobs) {
            for (user in users) {
                val key = JobKey(mode, user.id)
                val existing = activeJobs[key]
                if (existing != null && !existing.state.isTerminal) {
                    alreadyActive += existing
                    continue
                }
                val round = if (mode == TaskMode.PURCHASE) calendar.salesRound(now) else null
                val deadline = round?.let { calendar.purchaseDeadline(it).toInstant() } ?: now.toInstant().plus(CHECK_RETRY_WINDOW)
                UserJob(execution.id, user, mode, round, deadline).also {
                    activeJobs[key] = it
                    created += it
                }
            }
        }
        log.info("[Job] Start mode={} trigger={} created={} alreadyActive={}",
            mode, command.trigger, created.map { it.user.id }, alreadyActive.map { it.user.id })
        if (created.isEmpty()) {
            return StartResult(null, alreadyActive.map { it.view() })
        }
        execution.jobs += created
        remember(execution)
        created.forEach { schedule(it, now.toInstant(), JobState.QUEUED) }
        return StartResult(execution.view(), alreadyActive.map { it.view() })
    }

    override fun find(executionId: String): ExecutionView? = executions[executionId]?.view()

    override fun recent(): List<ExecutionView> = executions.values.sortedByDescending { it.createdAt }.map { it.view() }

    private fun schedule(job: UserJob, at: Instant, state: JobState) {
        val generation = synchronized(job) {
            job.state = state
            job.nextAttemptAt = at
            ++job.generation
        }
        taskScheduler.schedule({ executor.execute { runAttempt(job, generation) } }, at)
    }

    private fun runAttempt(job: UserJob, generation: Long) {
        synchronized(job) {
            if (job.generation != generation || job.state.isTerminal) {
                return
            }
        }
        if (!accepting) {
            finish(job, JobState.ABORTED, "서비스 종료로 중단") { notifyShutdown(job) }
            return
        }
        if (!accountLocks.tryAcquire(job.user.id)) {
            // 같은 계정의 다른 작업(확인/구매)이 브라우저를 쓰는 중이다. 시도로 세지 않고 잠시 뒤 다시 본다.
            schedule(job, now().plus(ACCOUNT_BUSY_DELAY), job.state)
            return
        }
        try {
            synchronized(job) {
                job.state = JobState.RUNNING
                job.attempts++
                job.nextAttemptAt = null
                job.abortSignal.set(false)
                // stop()이 위 확인과 이 초기화 사이에 중단 신호를 보냈다면 지워지지 않도록 다시 켠다.
                if (!accepting) {
                    job.abortSignal.set(true)
                }
            }
            log.info("[Job][{}] Attempt {} mode={} round={}", job.user.id, job.attempts, job.mode, job.round)
            val watchdog = taskScheduler.schedule({ job.abortSignal.set(true) }, now().plus(ATTEMPT_LIMIT))
            val result = try {
                attemptRunner.run(job.user, job.mode, job.abortSignal)
            } finally {
                watchdog.cancel(false)
            }
            decide(job, result)
        } catch (failure: Throwable) {
            log.error("[Job][{}] Attempt crashed mode={}", job.user.id, job.mode, failure)
            val error = failure as? Exception ?: IllegalStateException(failure.message, failure)
            finish(job, JobState.FAILED, "예상치 못한 오류: ${failure.message}") { notifier.failure(job.user, job.mode, error, null) }
            if (failure is Error) {
                throw failure
            }
        } finally {
            accountLocks.release(job.user.id)
        }
    }

    private fun decide(job: UserJob, result: AttemptResult) {
        if (result !is AttemptResult.Failed) {
            // 로그인이 된 시도가 있었으면 '연속 거부'가 끊긴 것이다.
            job.resetLoginRejections()
        }
        when (result) {
            is AttemptResult.Purchase -> decidePurchase(job, result)
            is AttemptResult.Checked -> decideCheck(job, result)
            is AttemptResult.Failed -> decideFailure(job, result)
        }
    }

    private fun decidePurchase(job: UserJob, result: AttemptResult.Purchase) {
        val notifyOutcome = { notifier.purchase(job.user, result.account, result.outcome, result.trace) }
        when (val outcome = result.outcome) {
            is PurchaseOutcome.Purchased -> finish(job, JobState.SUCCEEDED, outcome.note, notifyOutcome)
            is PurchaseOutcome.AlreadyPurchased -> finish(job, JobState.ALREADY_PURCHASED, null, notifyOutcome)
            is PurchaseOutcome.DryRun -> finish(job, JobState.SUCCEEDED, "dry-run: ${outcome.plan.toBuy}장 직전에 멈춤", null)
            is PurchaseOutcome.Refused -> finish(job, JobState.FAILED, outcome.reason, notifyOutcome)
            is PurchaseOutcome.Rejected -> finish(job, JobState.FAILED, "사이트 거부: ${outcome.siteMessages}", notifyOutcome)
            is PurchaseOutcome.NotOnSale ->
                if (job.round != null && outcome.nextRound != job.round) {
                    finish(job, JobState.FAILED, "${job.round}회차 판매 마감", notifyOutcome)
                } else {
                    job.uncountAttempt()
                    schedule(job, outcome.nextPurchasableAt.toInstant().plus(jitter()), JobState.DEFERRED)
                }
            is PurchaseOutcome.Blocked -> {
                job.uncountAttempt()
                // 기동 직후 보류도 여기로 오므로 '클릭 후 판정 불가(UNCERTAIN)'로 표시하지 않는다.
                retryOrFinish(job, outcome.until, JobState.RETRY_SCHEDULED, "원장 반영 대기(직전 구매 확인 또는 기동 직후)", notifyOutcome)
            }
            is PurchaseOutcome.Uncertain ->
                retryOrFinish(job, outcome.recheckAfter, JobState.UNCERTAIN, "확정 클릭 후 구매 확인 불가", notifyOutcome)
            is PurchaseOutcome.Aborted -> retryOrFinish(job, now().plus(backoff(job.attempts)), JobState.RETRY_SCHEDULED,
                "시도 시간 초과로 중단(${outcome.phase})") {
                notifier.failure(job.user, job.mode, IllegalStateException("구매가 최종 확정 전에 중단되었습니다 (${outcome.phase})"), result.trace)
            }
        }
    }

    private fun decideCheck(job: UserJob, result: AttemptResult.Checked) {
        val notifyResults = { notifier.check(job.user, result.account, result.results) }
        val latestRound = calendar.latestDrawnRound(calendar.now())
        val resultNotPosted = result.results.any {
            it.result.round.toIntOrNull() == latestRound && it.result.result == MessageType.WAIT.text
        }
        if (resultNotPosted) {
            retryOrFinish(job, now().plus(RESULT_RECHECK_DELAY), JobState.RETRY_SCHEDULED, "${latestRound}회차 결과 미반영", notifyResults)
        } else {
            finish(job, JobState.SUCCEEDED, null, notifyResults)
        }
    }

    private fun decideFailure(job: UserJob, result: AttemptResult.Failed) {
        val notifyFailure = { notifier.failure(job.user, job.mode, result.error, result.trace) }
        if (generateSequence<Throwable>(result.error) { it.cause }.any { it is LoginRejectedException }) {
            // 비밀번호 오류를 계속 시도하면 계정이 잠긴다. 사이트 장애(시간 초과 등)와 구분해 연속 거부만 센다.
            if (job.recordLoginRejection() >= MAX_LOGIN_REJECTIONS) {
                finish(job, JobState.FAILED, "로그인 거부 ${MAX_LOGIN_REJECTIONS}회 연속 - 계정 잠김 방지를 위해 중단", notifyFailure)
                return
            }
        } else {
            job.resetLoginRejections()
        }
        retryOrFinish(job, now().plus(backoff(job.attempts)), JobState.RETRY_SCHEDULED, result.error.message, notifyFailure)
    }

    private fun retryOrFinish(job: UserJob, at: Instant, state: JobState, reason: String?, finalNotice: () -> Unit) {
        when {
            !accepting -> finish(job, JobState.ABORTED, "서비스 종료로 중단: $reason", finalNotice)
            job.attempts >= MAX_ATTEMPTS -> finish(job, JobState.FAILED, "재시도 ${MAX_ATTEMPTS}회 소진: $reason", finalNotice)
            at.isAfter(job.deadline) -> finish(job, JobState.FAILED, "재시도 마감(${job.deadline}) 도달: $reason", finalNotice)
            else -> {
                synchronized(job) { job.lastError = reason }
                log.warn("[Job][{}] Retry at {} state={} reason={}", job.user.id, at, state, reason)
                schedule(job, at, state)
            }
        }
    }

    private fun finish(job: UserJob, state: JobState, reason: String?, notify: (() -> Unit)?) {
        synchronized(job) {
            job.state = state
            job.lastError = reason
            job.nextAttemptAt = null
            job.generation++
        }
        synchronized(activeJobs) { activeJobs.remove(JobKey(job.mode, job.user.id), job) }
        log.info("[Job][{}] Finished mode={} state={} attempts={} reason={}", job.user.id, job.mode, state, job.attempts, reason)
        notify?.invoke()
    }

    private fun remember(execution: Execution) {
        executions[execution.id] = execution
        val cutoff = now().minus(HISTORY_RETENTION)
        val finished = executions.values.filter { it.isFinished() }.sortedBy { it.createdAt }
        finished.filter { it.createdAt.isBefore(cutoff) }.forEach { executions.remove(it.id) }
        val overflow = executions.size - MAX_HISTORY
        if (overflow > 0) {
            finished.take(overflow).forEach { executions.remove(it.id) }
        }
    }

    private fun resolveTargetUsers(requestedUserIds: List<String>): List<LottoUser> {
        val allUsers = loadLottoUsersPort.loadUsers()
        if (requestedUserIds.isEmpty()) {
            return allUsers
        }
        val usersById = LinkedHashMap<String, LottoUser>()
        allUsers.forEach { usersById.putIfAbsent(it.id, it) }
        val invalidUserIds = requestedUserIds.filterNot { it in usersById }
        if (invalidUserIds.isNotEmpty()) {
            throw InvalidManualUserSelectionException(invalidUserIds, usersById.keys.toList())
        }
        return requestedUserIds.mapNotNull { usersById[it] }
    }

    private fun now(): Instant = calendar.now().toInstant()

    override fun start() {
        lifecycleRunning = true
    }

    /**
     * 새 시도를 막고 실행 중인 시도에 중단 신호를 보낸 뒤(결제 직전이면 멈춤) 끝날 때까지 기다린다.
     * 다음 시도를 기다리던 작업은 재시작하면 사라지므로 중단으로 끝내고 알린다.
     */
    override fun stop() {
        accepting = false
        val active = synchronized(activeJobs) { activeJobs.values.toList() }
        active.forEach { it.abortSignal.set(true) }
        val waitUntil = System.nanoTime() + SHUTDOWN_WAIT.toNanos()
        while (!accountLocks.isIdle() && System.nanoTime() < waitUntil) {
            Thread.sleep(SHUTDOWN_POLL.toMillis())
        }
        active.filter { !it.state.isTerminal && it.state != JobState.RUNNING }
            .forEach { job -> finish(job, JobState.ABORTED, "서비스 종료로 중단(이전 상태 ${job.state})") { notifyShutdown(job) } }
        val stillRunning = active.filter { it.state == JobState.RUNNING }
        if (stillRunning.isNotEmpty()) {
            log.error("[Job] Shutting down while {} attempt(s) still running: {}",
                stillRunning.size, stillRunning.map { "${it.mode}:${it.user.id}" })
        }
        lifecycleRunning = false
    }

    private fun notifyShutdown(job: UserJob) {
        val detail = if (job.mode == TaskMode.PURCHASE) {
            "구매 여부는 동행복권 구매내역에서 확인하고, 필요하면 다시 실행하세요(구매 전 확인이 있어 다시 실행해도 이중 구매되지 않습니다)."
        } else {
            "필요하면 결과 확인을 다시 실행하세요."
        }
        notifier.failure(job.user, job.mode, IllegalStateException("서비스 재시작/종료로 작업이 중단되었습니다. $detail"), null)
    }

    override fun isRunning(): Boolean = lifecycleRunning

    /** 웹 서버가 멈춘 뒤, 실행기(lottoTaskExecutor)가 종료되기 전에 멈춰 실행 중인 시도를 정리한다. */
    override fun getPhase(): Int = Int.MAX_VALUE - 1_000

    private data class JobKey(val mode: TaskMode, val userId: String)

    private class Execution(val id: String, val mode: TaskMode, val trigger: TriggerType, val createdAt: Instant) {
        val jobs = CopyOnWriteArrayList<UserJob>()

        fun isFinished(): Boolean = jobs.all { it.state.isTerminal }

        fun view() = ExecutionView(id, mode, trigger, createdAt, jobs.map { it.view() })
    }

    private class UserJob(
        val executionId: String,
        val user: LottoUser,
        val mode: TaskMode,
        val round: Int?,
        val deadline: Instant,
    ) {
        @Volatile
        var state: JobState = JobState.QUEUED
        var attempts = 0
        var nextAttemptAt: Instant? = null
        var lastError: String? = null
        var generation = 0L
        val abortSignal = AtomicBoolean(false)
        private var loginRejections = 0

        @Synchronized
        fun uncountAttempt() {
            attempts = maxOf(0, attempts - 1)
        }

        @Synchronized
        fun recordLoginRejection(): Int = ++loginRejections

        @Synchronized
        fun resetLoginRejections() {
            loginRejections = 0
        }

        @Synchronized
        fun view() = UserJobView(executionId, user.id, mode, state, attempts, round, nextAttemptAt, lastError)
    }

    companion object {
        const val MAX_ATTEMPTS = 12
        const val MAX_LOGIN_REJECTIONS = 2
        val INITIAL_RETRY_DELAY: Duration = Duration.ofMinutes(5)
        val MAX_RETRY_DELAY: Duration = Duration.ofMinutes(60)

        /** 감시 타이머: 이 시간이 지나면 중단 신호를 켠다(결제 직전이면 결제하지 않음). */
        val ATTEMPT_LIMIT: Duration = Duration.ofMinutes(15)
        val ACCOUNT_BUSY_DELAY: Duration = Duration.ofSeconds(30)
        val RESULT_RECHECK_DELAY: Duration = Duration.ofMinutes(30)
        val CHECK_RETRY_WINDOW: Duration = Duration.ofHours(12)
        val HISTORY_RETENTION: Duration = Duration.ofHours(24)
        const val MAX_HISTORY = 50
        val SHUTDOWN_WAIT: Duration = Duration.ofSeconds(60)
        private val SHUTDOWN_POLL: Duration = Duration.ofMillis(200)
        private const val MAX_DEFER_JITTER_SECONDS = 60L

        /** 시도 횟수에 따른 대기: 5분에서 시작해 두 배씩, 최대 60분. */
        fun backoff(attempts: Int): Duration {
            val exponent = (attempts - 1).coerceIn(0, 10)
            return INITIAL_RETRY_DELAY.multipliedBy(1L shl exponent).coerceAtMost(MAX_RETRY_DELAY)
        }

        /** 판매 시작 시각에 여러 계정이 몰리지 않도록 흩는다. */
        private fun jitter(): Duration = Duration.ofSeconds(ThreadLocalRandom.current().nextLong(0, MAX_DEFER_JITTER_SECONDS))
    }
}
