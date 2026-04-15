package org.nowstart.lotto.application.dto;

import java.util.List;
import org.nowstart.lotto.domain.type.TriggerType;

public record CheckLottoResultsCommand(
        TriggerType trigger,
        List<String> userIds
) {
    public CheckLottoResultsCommand {
        userIds = userIds == null ? null : List.copyOf(userIds);
    }
}
