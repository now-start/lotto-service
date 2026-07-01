package org.nowstart.lotto.application.port.out;

public interface LottoAutomationSession extends AutoCloseable {

    default void markFailed() {
    }

    @Override
    void close();
}
