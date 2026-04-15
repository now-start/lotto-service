package org.nowstart.lotto.application.port.in;

import java.util.List;
import java.util.Objects;
import org.nowstart.lotto.domain.model.LottoExecution;
import org.nowstart.lotto.domain.type.TriggerType;

public interface LottoUseCase {

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

    LottoExecution check(TargetCommand command);

    LottoExecution purchase(TargetCommand command);
}
