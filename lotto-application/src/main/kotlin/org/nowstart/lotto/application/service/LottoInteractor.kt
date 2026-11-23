package org.nowstart.lotto.application.service

import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import org.nowstart.lotto.application.exception.InvalidManualUserSelectionException
import org.nowstart.lotto.application.port.inbound.LottoExecution
import org.nowstart.lotto.application.port.inbound.LottoUseCase
import org.nowstart.lotto.application.port.inbound.TargetCommand
import org.nowstart.lotto.application.port.outbound.LoadLottoUsersPort
import org.nowstart.lotto.application.port.outbound.LottoUser
import org.nowstart.lotto.domain.type.ExecutionStatus
import org.nowstart.lotto.domain.type.TaskMode
import org.nowstart.lotto.domain.type.TriggerType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

private val log = LoggerFactory.getLogger(LottoInteractor::class.java)

@Service
class LottoInteractor(
    private val loadLottoUsersPort: LoadLottoUsersPort,
    private val lottoUserRunner: LottoUserRunner,
    private val executionSettings: ExecutionSettings,
) : LottoUseCase {

    // 인스턴스 내 중복 실행 방지(수동 API + 스케줄 동시 실행 등). 다중 인스턴스 환경은 분산 락/리더 선출이 별도로 필요하다.
    // 키는 실제 비동기 작업이 종료될 때(whenComplete) 해제한다 — 호출자 타임아웃 시점에 해제하면
    // 작업이 백그라운드에서 계속 도는 동안 같은 사용자의 중복 실행이 시작될 수 있으므로 금물.
    private val inFlightKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override fun check(command: TargetCommand): LottoExecution = execute(command.trigger, command.userIds, TaskMode.CHECK)

    override fun purchase(command: TargetCommand): LottoExecution = execute(command.trigger, command.userIds, TaskMode.PURCHASE)

    private fun execute(trigger: TriggerType, requestedUserIds: List<String>, mode: TaskMode): LottoExecution {
        val targetUsers = resolveTargetUsers(requestedUserIds)
        log.info("[Task] Batch start mode={} trigger={} userCount={}", mode, trigger, targetUsers.size)

        val startedAt = Instant.now()
        val startedNano = System.nanoTime()
        val counts = runUsers(targetUsers, mode)
        // 이 호출에서 실행되지 못한 skip 사용자(다른 실행이 선점)도 '비성공'으로 집계한다.
        // 그러지 않으면 전량 skip 시 totalUsers=0 → ExecutionStatus.fromCounts(0,0)=SUCCESS 로 오인된다.
        val failedUsers = counts.failedUsers + counts.skippedUsers
        val endedAt = Instant.now()
        val durationMs = (System.nanoTime() - startedNano) / 1_000_000
        val totalUsers = counts.successUsers + failedUsers
        val status = ExecutionStatus.fromCounts(totalUsers, failedUsers)

        log.info("[Task] Batch complete mode={} trigger={} status={} success={} failed={} skipped={} durationMs={}",
            mode, trigger, status, counts.successUsers, counts.failedUsers, counts.skippedUsers, durationMs)
        return LottoExecution(mode, trigger, status, startedAt, endedAt, durationMs, totalUsers, counts.successUsers, failedUsers)
    }

    private fun runUsers(targetUsers: List<LottoUser>, mode: TaskMode): UserExecutionCounts {
        val userTasks = mutableListOf<UserTask>()
        var skippedUsers = 0
        for (user in targetUsers) {
            // 계정 단위 키: 같은 계정은 확인(CHECK)과 구매(PURCHASE)도 동시에 실행하지 않는다
            // (두 번째 로그인이 구매 세션을 끊어 확정 클릭 직후 상태를 잃는 것을 방지).
            val key = user.id
            if (!inFlightKeys.add(key)) {
                skippedUsers++
                log.warn("[Task][{}] Skip - 같은 계정 작업이 이미 실행 중 mode={}", user.id, mode)
                continue
            }
            // per-user 타임아웃 기준점을 '제출 시점'으로 고정한다 — join 시점 기준이면 앞선 hung 사용자를
            // 기다린 시간만큼 뒤 사용자의 제한이 늘어나(N배) 설정값을 크게 초과할 수 있다.
            val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(executionSettings.userTaskTimeoutMs)
            val abortSignal = AtomicBoolean(false)
            val future = try {
                lottoUserRunner.runAsync(user, mode, abortSignal)
            } catch (submitError: Throwable) {
                // 제출 실패 시: 현재 사용자 키 해제 + 이미 제출된 이전 작업들도 중단 신호/취소하여,
                // 실패한 이 호출 이후 이전 작업이 뒤늦게 실구매하지 않도록 한다.
                inFlightKeys.remove(key)
                cancelRemaining(userTasks)
                throw submitError
            }
            // 실제 작업 종료(성공/실패/취소) 시점에만 in-flight 키를 해제한다.
            future.whenComplete { _, _ -> inFlightKeys.remove(key) }
            userTasks += UserTask(user, deadlineNanos, abortSignal, future)
        }

        try {
            var successUsers = 0
            var failedUsers = 0
            for (userTask in userTasks) {
                if (joinUserTask(userTask, mode)) successUsers++ else failedUsers++
            }
            return UserExecutionCounts(successUsers, failedUsers, skippedUsers)
        } catch (error: Error) {
            cancelRemaining(userTasks)
            throw error
        }
    }

    private fun joinUserTask(userTask: UserTask, mode: TaskMode): Boolean {
        val remainingMs = maxOf(0L, (userTask.deadlineNanos - System.nanoTime()) / 1_000_000L)
        return try {
            userTask.future.get(remainingMs, TimeUnit.MILLISECONDS)
        } catch (exception: ExecutionException) {
            val cause = exception.cause
            if (cause is Error) {
                throw cause
            }
            log.error("[Task][{}] Failed mode={} step=async", userTask.user.id, mode, cause ?: exception)
            false
        } catch (exception: TimeoutException) {
            // 아직 실행/큐잉 중인 작업에 중단 신호를 보낸다 — 세마포어 대기 중이던 작업이 뒤늦게
            // 실거래(구매)를 수행하는 것을 방지(러너가 구매 직전 abortSignal을 확인한다).
            userTask.abortSignal.set(true)
            if (mode == TaskMode.PURCHASE) {
                // 최종 확정 이후 타임아웃이면 구매가 성사됐을 수 있어 결과는 '불확실'하다(단정된 실패 아님).
                log.error("[Task][{}] PURCHASE timed out mode={} (제한 {}ms 초과) - 결과 불확실: " +
                        "실제 구매 성사 여부는 백그라운드 원장 확인/메일로 판정, 중복 방지 위해 in-flight 키 유지",
                    userTask.user.id, mode, executionSettings.userTaskTimeoutMs)
            } else {
                log.error("[Task][{}] Timed out mode={} (제한 {}ms 초과 - 중단 신호 전송, 작업은 안전 지점에서 종료됨)",
                    userTask.user.id, mode, executionSettings.userTaskTimeoutMs)
            }
            false
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            userTask.abortSignal.set(true)
            log.error("[Task][{}] Interrupted mode={}", userTask.user.id, mode, exception)
            false
        }
    }

    private fun cancelRemaining(userTasks: List<UserTask>) {
        userTasks.forEach { userTask ->
            userTask.abortSignal.set(true)
            if (!userTask.future.isDone) {
                userTask.future.cancel(true)
            }
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

    private data class UserTask(
        val user: LottoUser,
        val deadlineNanos: Long,
        val abortSignal: AtomicBoolean,
        val future: CompletableFuture<Boolean>,
    )

    private data class UserExecutionCounts(val successUsers: Int, val failedUsers: Int, val skippedUsers: Int)
}
