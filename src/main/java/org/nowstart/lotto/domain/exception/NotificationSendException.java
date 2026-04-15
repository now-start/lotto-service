package org.nowstart.lotto.domain.exception;

public class NotificationSendException extends RuntimeException {

    public NotificationSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
