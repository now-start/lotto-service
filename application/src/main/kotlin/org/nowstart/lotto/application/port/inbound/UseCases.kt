package org.nowstart.lotto.application.port.inbound

import java.time.Instant
import org.nowstart.lotto.domain.type.TaskMode
import org.nowstart.lotto.domain.type.TriggerType

/**
 * 로또 작업 실행. 호출은 즉시 반환되고, 실제 작업은 계정별 작업(job)으로 백그라운드에서 실행·재시도된다.
 */
interface JobUseCase {

    /** 계정별 작업을 시작한다. 같은 계정·모드의 작업이 이미 진행 중이면 새로 만들지 않고 기존 작업을 돌려준다. */
    fun start(mode: TaskMode, command: TargetCommand): StartResult

    fun find(executionId: String): ExecutionView?

    /** 최근 실행 기록(최신순). */
    fun recent(): List<ExecutionView>
}

interface InitializeUseCase {

    fun initialize()
}

/**
 * @property execution 새로 만든 실행. 모든 계정이 이미 진행 중이면 null
 * @property alreadyActive 이미 진행 중이라 새로 만들지 않은 계정 작업
 */
data class StartResult(val execution: ExecutionView?, val alreadyActive: List<UserJobView>)

data class ExecutionView(
    val id: String,
    val mode: TaskMode,
    val trigger: TriggerType,
    val createdAt: Instant,
    val jobs: List<UserJobView>,
)

data class UserJobView(
    val executionId: String,
    val userId: String,
    val mode: TaskMode,
    val state: JobState,
    val attempts: Int,
    val round: Int?,
    val nextAttemptAt: Instant?,
    val lastError: String?,
)

enum class JobState(val isTerminal: Boolean) {
    QUEUED(false),
    RUNNING(false),
    RETRY_SCHEDULED(false),

    /** 판매 시간 밖이라 다음 판매 시작에 실행한다. */
    DEFERRED(false),

    /** 확정 클릭 후 구매를 확인하지 못해, 원장 반영을 기다렸다가 다시 확인한다. */
    UNCERTAIN(false),
    SUCCEEDED(true),
    ALREADY_PURCHASED(true),
    FAILED(true),
    ABORTED(true),
}

/** 실행 대상. 비어 있으면 전체 사용자다. 공백·중복 id는 정리한다. */
class TargetCommand(val trigger: TriggerType, userIds: List<String?>? = null) {

    val userIds: List<String> = userIds.orEmpty()
        .filterNotNull()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

    companion object {
        fun all(trigger: TriggerType): TargetCommand = TargetCommand(trigger)
    }
}
