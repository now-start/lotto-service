package org.nowstart.lotto.data.dto;

import java.time.Instant;
import org.nowstart.lotto.data.type.ExecutionStatus;
import org.nowstart.lotto.data.type.TaskMode;
import org.nowstart.lotto.data.type.TriggerType;

public record BatchExecutionResult(
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
}
