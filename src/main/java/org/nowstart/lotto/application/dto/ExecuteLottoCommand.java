package org.nowstart.lotto.application.dto;

import java.util.List;
import org.nowstart.lotto.domain.type.TaskMode;
import org.nowstart.lotto.domain.type.TriggerType;

public record ExecuteLottoCommand(
        TaskMode mode,
        TriggerType trigger,
        List<String> userIds
) {
    public ExecuteLottoCommand {
        userIds = userIds == null ? null : List.copyOf(userIds);
    }
}
