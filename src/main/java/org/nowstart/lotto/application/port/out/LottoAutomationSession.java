package org.nowstart.lotto.application.port.out;

public interface LottoAutomationSession extends AutoCloseable {

    default void markFailed() {
    }

    default SendNotificationPort.Attachment failureTrace() {
        return null;
    }

    @Override
    void close();
}
