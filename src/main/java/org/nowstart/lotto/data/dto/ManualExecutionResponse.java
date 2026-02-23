package org.nowstart.lotto.data.dto;

import java.time.Instant;
import org.nowstart.lotto.data.type.ExecutionStatus;
import org.nowstart.lotto.data.type.TaskMode;
import org.nowstart.lotto.data.type.TriggerType;

public record ManualExecutionResponse(
        TaskMode mode,
        TriggerType trigger,
        ExecutionStatus status,
        Instant startedAt,
        Instant endedAt,
        long durationMs,
        int totalUsers,
        int successUsers,
        int failedUsers
) {
    public static ManualExecutionResponse from(BatchExecutionResult batchExecutionResult) {
        return new ManualExecutionResponse(
                batchExecutionResult.mode(),
                batchExecutionResult.trigger(),
                batchExecutionResult.status(),
                batchExecutionResult.startedAt(),
                batchExecutionResult.endedAt(),
                batchExecutionResult.durationMs(),
                batchExecutionResult.totalUsers(),
                batchExecutionResult.successUsers(),
                batchExecutionResult.failedUsers()
        );
    }
}
