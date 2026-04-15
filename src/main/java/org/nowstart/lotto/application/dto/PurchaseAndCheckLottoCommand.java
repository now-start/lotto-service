package org.nowstart.lotto.application.dto;

import java.util.List;
import org.nowstart.lotto.domain.type.TriggerType;

public record PurchaseAndCheckLottoCommand(
        TriggerType trigger,
        List<String> userIds
) {
    public PurchaseAndCheckLottoCommand {
        userIds = userIds == null ? null : List.copyOf(userIds);
    }
}
