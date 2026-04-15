package org.nowstart.lotto.application.dto;

import java.util.List;
import org.nowstart.lotto.domain.type.TriggerType;

public record PurchaseLottoCommand(
        TriggerType trigger,
        List<String> userIds
) {
    public PurchaseLottoCommand {
        userIds = userIds == null ? null : List.copyOf(userIds);
    }
}
