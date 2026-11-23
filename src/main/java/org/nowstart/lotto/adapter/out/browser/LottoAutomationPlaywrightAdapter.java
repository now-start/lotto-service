package org.nowstart.lotto.adapter.out.browser;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.nowstart.lotto.application.port.out.LottoAutomationPort;
import org.nowstart.lotto.application.port.out.LottoAutomationSession;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort.LottoUser;
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
        LottoAccountSnapshot snapshot = playwrightLoginExecutor.login(playwrightSessionManager.requirePage(session), user);
        // 비밀번호 입력값이 트레이스에 남지 않도록 로그인을 마친 뒤 기록을 시작한다.
        playwrightSessionManager.startTracing(session);
        return snapshot;
    }

    @Override
    public LottoAccountSnapshot readAccount(LottoAutomationSession session) {
        return playwrightLoginExecutor.readAccount(playwrightSessionManager.requirePage(session));
    }

    @Override
    public List<LottoResult> readLedger(LottoAutomationSession session) {
        return playwrightResultExecutor.readLedger(playwrightSessionManager.requirePage(session));
    }

    @Override
    public Optional<List<LottoResult>> awaitLedger(
            LottoAutomationSession session,
            Predicate<List<LottoResult>> condition,
            Duration timeout,
            BooleanSupplier abortRequested
    ) {
        return playwrightResultExecutor.awaitLedger(
                playwrightSessionManager.requirePage(session), condition, timeout, abortRequested);
    }

    @Override
    public List<CheckResult> captureTickets(LottoAutomationSession session, List<LottoResult> rows) {
        return playwrightResultExecutor.captureTickets(playwrightSessionManager.requirePage(session), rows);
    }

    @Override
    public PurchaseSubmission buy(LottoAutomationSession session, LottoUser user, int count, BooleanSupplier abortRequested) {
        return playwrightPurchaseExecutor.buy(playwrightSessionManager.requirePage(session), user, count, abortRequested);
    }

    @Override
    public List<CheckResult> check(LottoAutomationSession session) {
        return playwrightResultExecutor.check(playwrightSessionManager.requirePage(session));
    }
}
