package org.nowstart.lotto.data.dto;

import java.util.List;

public record ManualExecutionErrorResponse(
        String message,
        List<String> invalidUserIds,
        List<String> availableUserIds
) {
}
