package org.nowstart.lotto.domain.model;

import java.time.Instant;
import org.nowstart.lotto.domain.type.ExecutionStatus;
import org.nowstart.lotto.domain.type.TaskMode;
import org.nowstart.lotto.domain.type.TriggerType;

public record LottoExecution(
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
