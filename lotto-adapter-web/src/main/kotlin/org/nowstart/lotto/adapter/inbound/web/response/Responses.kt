package org.nowstart.lotto.adapter.inbound.web.response

import java.time.Instant
import org.nowstart.lotto.application.port.inbound.LottoExecution
import org.nowstart.lotto.domain.type.ExecutionStatus
import org.nowstart.lotto.domain.type.TaskMode
import org.nowstart.lotto.domain.type.TriggerType

data class LottoExecutionResponse(
    val mode: TaskMode,
    val trigger: TriggerType,
    val status: ExecutionStatus,
    val startedAt: Instant,
    val endedAt: Instant,
    val durationMs: Long,
    val totalUsers: Int,
    val successUsers: Int,
    val failedUsers: Int,
) {
    companion object {
        fun from(execution: LottoExecution) = LottoExecutionResponse(
            execution.mode,
            execution.trigger,
            execution.status,
            execution.startedAt,
            execution.endedAt,
            execution.durationMs,
            execution.totalUsers,
            execution.successUsers,
            execution.failedUsers,
        )
    }
}

data class ManualExecutionErrorResponse(
    val message: String,
    val invalidUserIds: List<String>,
    val availableUserIds: List<String>,
)
