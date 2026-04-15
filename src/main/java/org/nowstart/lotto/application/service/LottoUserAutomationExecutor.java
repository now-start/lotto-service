package org.nowstart.lotto.application.service;

import java.util.List;
import java.util.function.Supplier;
import org.nowstart.lotto.application.port.out.LottoAutomationPort;
import org.nowstart.lotto.application.port.out.LottoAutomationSession;
import org.nowstart.lotto.domain.exception.LottoAutomationException;
import org.nowstart.lotto.domain.model.LottoAccountSnapshot;
import org.nowstart.lotto.domain.model.LottoResult;
import org.nowstart.lotto.domain.model.LottoUser;
import org.nowstart.lotto.domain.type.StepType;

final class LottoUserAutomationExecutor {

    private final LottoAutomationPort lottoAutomationPort;
    private final LottoAutomationSession session;
    private final LottoUser user;

    LottoUserAutomationExecutor(
            LottoAutomationPort lottoAutomationPort,
            LottoAutomationSession session,
            LottoUser user
    ) {
        this.lottoAutomationPort = lottoAutomationPort;
        this.session = session;
        this.user = user;
    }

    LottoAccountSnapshot login() {
        return runStep(StepType.LOGIN, () -> lottoAutomationPort.login(session, user));
    }

    void buy() {
        runStep(StepType.PURCHASE, () -> {
            lottoAutomationPort.buy(session, user);
            return null;
        });
    }

    List<LottoResult> check() {
        return runStep(StepType.CHECK, () -> lottoAutomationPort.check(session));
    }

    private <T> T runStep(StepType stepType, Supplier<T> action) {
        try {
            return action.get();
        } catch (LottoAutomationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new LottoAutomationException(stepType, user.id(), exception);
        }
    }
}
