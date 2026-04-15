package org.nowstart.lotto.adapter.out.browser;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.nowstart.lotto.application.port.out.LottoAutomationPort;
import org.nowstart.lotto.application.port.out.LottoAutomationSession;
import org.nowstart.lotto.domain.model.LottoAccountSnapshot;
import org.nowstart.lotto.domain.model.LottoResult;
import org.nowstart.lotto.domain.model.LottoUser;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LottoAutomationPlaywrightAdapter implements LottoAutomationPort {

    private final PlaywrightSessionManager playwrightSessionManager;
    private final PlaywrightLoginExecutor playwrightLoginExecutor;
    private final PlaywrightPurchaseExecutor playwrightPurchaseExecutor;
    private final PlaywrightResultExecutor playwrightResultExecutor;

    @Override
    public LottoAutomationSession openSession() {
        return playwrightSessionManager.openSession();
    }

    @Override
    public LottoAccountSnapshot login(LottoAutomationSession session, LottoUser user) {
        return playwrightLoginExecutor.login(playwrightSessionManager.requirePage(session), user);
    }

    @Override
    public void buy(LottoAutomationSession session, LottoUser user) {
        playwrightPurchaseExecutor.buy(playwrightSessionManager.requirePage(session), user);
    }

    @Override
    public List<LottoResult> check(LottoAutomationSession session) {
        return playwrightResultExecutor.check(playwrightSessionManager.requirePage(session));
    }
}
