package org.nowstart.lotto.adapter.in.web.response;

import java.util.List;

public record ManualExecutionErrorResponse(
        String message,
        List<String> invalidUserIds,
        List<String> availableUserIds
) {
}
