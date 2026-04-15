package org.nowstart.lotto.adapter.in.web.response;

import java.time.Instant;
import org.nowstart.lotto.domain.model.LottoExecution;
import org.nowstart.lotto.domain.type.ExecutionStatus;
import org.nowstart.lotto.domain.type.TaskMode;
import org.nowstart.lotto.domain.type.TriggerType;

public record LottoExecutionResponse(
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
    public static LottoExecutionResponse from(LottoExecution execution) {
        return new LottoExecutionResponse(
                execution.mode(),
                execution.trigger(),
                execution.status(),
                execution.startedAt(),
                execution.endedAt(),
                execution.durationMs(),
                execution.totalUsers(),
                execution.successUsers(),
                execution.failedUsers()
        );
    }
}
