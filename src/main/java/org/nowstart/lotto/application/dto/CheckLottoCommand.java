package org.nowstart.lotto.application.dto;

import java.util.List;
import org.nowstart.lotto.domain.type.TriggerType;

public record CheckLottoCommand(
        TriggerType trigger,
        List<String> userIds
) {
    public CheckLottoCommand {
        userIds = userIds == null ? null : List.copyOf(userIds);
    }
}
