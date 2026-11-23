package org.nowstart.lotto.application.port.inbound

import java.time.Instant
import org.nowstart.lotto.domain.type.ExecutionStatus
import org.nowstart.lotto.domain.type.TaskMode
import org.nowstart.lotto.domain.type.TriggerType

interface LottoUseCase {

    fun check(command: TargetCommand): LottoExecution

    fun purchase(command: TargetCommand): LottoExecution
}

interface InitializeLottoUseCase {

    fun initialize()
}

data class LottoExecution(
    val mode: TaskMode,
    val trigger: TriggerType,
    val status: ExecutionStatus,
    val startedAt: Instant,
    val endedAt: Instant,
    val durationMs: Long,
    val totalUsers: Int,
    val successUsers: Int,
    val failedUsers: Int,
)

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
