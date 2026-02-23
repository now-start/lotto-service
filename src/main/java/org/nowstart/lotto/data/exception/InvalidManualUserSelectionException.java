package org.nowstart.lotto.data.exception;

import java.util.List;
import lombok.Getter;

@Getter
public class InvalidManualUserSelectionException extends RuntimeException {

    private final List<String> invalidUserIds;
    private final List<String> availableUserIds;

    public InvalidManualUserSelectionException(List<String> invalidUserIds, List<String> availableUserIds) {
        super(String.format("Invalid userIds: %s", invalidUserIds));
        this.invalidUserIds = List.copyOf(invalidUserIds);
        this.availableUserIds = List.copyOf(availableUserIds);
    }
}
