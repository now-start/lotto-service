package org.nowstart.lotto.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.nowstart.lotto.domain.type.ExecutionStatus;
import org.nowstart.lotto.domain.type.TaskMode;
import org.nowstart.lotto.domain.type.TriggerType;

public interface LottoUseCase {

    LottoExecution check(TargetCommand command);

    LottoExecution purchase(TargetCommand command);

    record LottoExecution(
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

    record TargetCommand(
            TriggerType trigger,
            List<String> userIds
    ) {
        public TargetCommand {
            Objects.requireNonNull(trigger, "trigger must not be null");
            userIds = userIds == null
                    ? List.of()
                    : userIds.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(id -> !id.isEmpty())
                    .distinct()
                    .toList();
        }

        public static TargetCommand all(TriggerType trigger) {
            return new TargetCommand(trigger, List.of());
        }
    }
}
