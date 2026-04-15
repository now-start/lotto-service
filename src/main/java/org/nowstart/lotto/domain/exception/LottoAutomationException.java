package org.nowstart.lotto.domain.exception;

import lombok.Getter;
import org.nowstart.lotto.domain.type.StepType;

@Getter
public class LottoAutomationException extends RuntimeException {

    private final StepType stepType;
    private final String userId;

    public LottoAutomationException(StepType stepType, String userId, Throwable cause) {
        super(stepType + " step failed for user " + userId, cause);
        this.stepType = stepType;
        this.userId = userId;
    }
}
