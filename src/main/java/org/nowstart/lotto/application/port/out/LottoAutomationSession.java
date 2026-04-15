package org.nowstart.lotto.application.port.out;

public interface LottoAutomationSession extends AutoCloseable {

    @Override
    void close();
}
