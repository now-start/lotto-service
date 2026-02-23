package org.nowstart.lotto.data.exception;

public class StepExecutionException extends RuntimeException {

    private final String step;
    private final String userId;

    public StepExecutionException(String step, String userId, Throwable cause) {
        super(step + " step failed for user " + userId, cause);
        this.step = step;
        this.userId = userId;
    }

    public String getStep() {
        return step;
    }

    public String getUserId() {
        return userId;
    }
}
