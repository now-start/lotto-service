package org.nowstart.lotto.domain.exception;

import java.util.List;
import lombok.Getter;

@Getter
public class InvalidManualUserSelectionException extends RuntimeException {

    private final List<String> invalidUserIds;
    private final List<String> availableUserIds;

    public InvalidManualUserSelectionException(List<String> invalidUserIds, List<String> availableUserIds) {
        super("유효하지 않은 userId가 포함되어 있습니다.");
        this.invalidUserIds = List.copyOf(invalidUserIds);
        this.availableUserIds = List.copyOf(availableUserIds);
    }
}
