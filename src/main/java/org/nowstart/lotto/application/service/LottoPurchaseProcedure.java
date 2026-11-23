package org.nowstart.lotto.application.service;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort.LottoUser;
import org.nowstart.lotto.application.port.out.LottoAutomationPort;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.CheckResult;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.LottoAccountSnapshot;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.LottoResult;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.PurchaseSubmission;
import org.nowstart.lotto.application.port.out.LottoAutomationSession;
import org.nowstart.lotto.domain.calendar.LottoDrawCalendar;
import org.nowstart.lotto.domain.ledger.LedgerEntry;
import org.nowstart.lotto.domain.ledger.LedgerMath;
import org.nowstart.lotto.domain.ledger.PurchasePlan;
import org.nowstart.lotto.domain.ledger.Won;
import org.springframework.stereotype.Service;

/**
 * 멱등한 구매 절차: 원장으로 이번 회차 구매 수량을 먼저 확인하고, 모자란 수량만 산다.
 *
 * <p>안전 규칙
 * <ul>
 *   <li>원장을 확실히 읽지 못하면 사지 않는다(판독 불가 예외가 그대로 올라간다).</li>
 *   <li>확정 클릭 뒤에는 긍정 증거(원장 새 행, 예치금 차감, 사이트 거부 문구)만 결과를 정한다. 없으면 {@link PurchaseOutcome.Uncertain}.</li>
 *   <li>구매가 확인된 뒤에는 티켓 캡처가 실패해도 성공이다.</li>
 *   <li>판정 대기 중인 (계정, 회차)는 원장 반영 지연 시간 동안 다시 사지 않는다.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LottoPurchaseProcedure {

    /** 확정 클릭 후 원장에 새 행이 보이기를 기다리는 시간. */
    static final Duration VERIFY_TIMEOUT = Duration.ofSeconds(60);
    /**
     * 사이트가 구매를 거부할 때 띄우는 대화상자 문구의 단서. 성공 안내에도 나오는 단어("예치금", "잔액")만으로는 거부로 보지 않는다.
     * 오인하더라도 거부 결과는 재구매 보류를 걸기 때문에 이중 구매로 이어지지 않는다.
     */
    private static final List<String> REJECTION_HINTS = List.of(
            "한도", "판매시간", "판매 시간", "구매할 수 없", "구매가 불가", "잔액이 부족", "예치금이 부족");

    private final LottoAutomationPort lottoAutomationPort;
    private final LottoDrawCalendar lottoDrawCalendar;
    private final UncertainPurchaseRegistry uncertainPurchaseRegistry;

    /** 브라우저를 열기 전에 판단할 수 있는 거부 사유(판매 시간, 판정 대기). */
    public Optional<PurchaseOutcome> precheck(LottoUser user) {
        ZonedDateTime now = lottoDrawCalendar.now();
        if (!lottoDrawCalendar.isPurchasable(now)) {
            ZonedDateTime next = lottoDrawCalendar.nextPurchasableAt(now);
            return Optional.of(new PurchaseOutcome.NotOnSale(next, lottoDrawCalendar.salesRound(next)));
        }
        int round = lottoDrawCalendar.salesRound(now);
        return uncertainPurchaseRegistry.blockedUntil(user.id(), round)
                .map(until -> new PurchaseOutcome.Blocked(round, until));
    }

    public PurchaseOutcome purchase(
            LottoAutomationSession session,
            LottoUser user,
            LottoAccountSnapshot account,
            BooleanSupplier abortRequested
    ) {
        // 로그인에 시간이 걸렸을 수 있으므로 판매 시간·판정 대기를 다시 확인한다.
        Optional<PurchaseOutcome> early = precheck(user);
        if (early.isPresent()) {
            return early.get();
        }
        int round = lottoDrawCalendar.salesRound(lottoDrawCalendar.now());

        List<LottoResult> before = lottoAutomationPort.readLedger(session);
        List<LedgerEntry> beforeEntries = toEntries(before);
        OptionalInt maxRound = LedgerMath.maxRound(beforeEntries);
        if (maxRound.isPresent() && maxRound.getAsInt() > round) {
            return new PurchaseOutcome.Refused("원장에 계산된 판매 회차(" + round + ")보다 큰 회차("
                    + maxRound.getAsInt() + ")가 있습니다. 회차 계산을 점검해야 합니다.");
        }

        PurchasePlan plan = PurchasePlan.of(round, user.count(), LedgerMath.purchasedCount(beforeEntries, round));
        log.info("[Purchase][{}] Plan round={} target={} already={} toBuy={}",
                user.id(), plan.round(), plan.targetCount(), plan.alreadyPurchased(), plan.toBuy());
        if (plan.isComplete()) {
            return new PurchaseOutcome.AlreadyPurchased(plan, captureQuietly(session, rowsOfRound(before, round)));
        }

        OptionalLong depositBefore = Won.parse(account.deposit());
        if (depositBefore.isPresent() && depositBefore.getAsLong() < plan.requiredDepositWon()) {
            return new PurchaseOutcome.Refused("예치금이 부족합니다: 보유 " + depositBefore.getAsLong()
                    + "원, 필요 " + plan.requiredDepositWon() + "원 (" + plan.toBuy() + "장)");
        }
        if (abortRequested.getAsBoolean()) {
            return new PurchaseOutcome.Aborted("구매 화면 진입 전");
        }

        PurchaseSubmission submission = lottoAutomationPort.buy(session, user, plan.toBuy(), abortRequested);
        return switch (submission.status()) {
            case NOT_CLICKED -> new PurchaseOutcome.Aborted("최종 확정 전");
            case DRY_RUN -> new PurchaseOutcome.DryRun(plan);
            case CLICKED -> verifySafely(session, user, plan, before, depositBefore, submission, abortRequested);
        };
    }

    /** 클릭 이후의 어떤 예외도 '구매 실패'로 바꾸지 않는다: 판정 불가로 보고 재구매를 보류한다. */
    private PurchaseOutcome verifySafely(
            LottoAutomationSession session,
            LottoUser user,
            PurchasePlan plan,
            List<LottoResult> before,
            OptionalLong depositBefore,
            PurchaseSubmission submission,
            BooleanSupplier abortRequested
    ) {
        try {
            return verify(session, user, plan, before, depositBefore, submission, abortRequested);
        } catch (RuntimeException exception) {
            log.error("[Purchase][{}] Verification failed after final click - treating as uncertain", user.id(), exception);
            return new PurchaseOutcome.Uncertain(plan, uncertainPurchaseRegistry.record(user.id(), plan.round()));
        }
    }

    private PurchaseOutcome verify(
            LottoAutomationSession session,
            LottoUser user,
            PurchasePlan plan,
            List<LottoResult> before,
            OptionalLong depositBefore,
            PurchaseSubmission submission,
            BooleanSupplier abortRequested
    ) {
        Set<String> keysBefore = before.stream().map(LottoResult::key).collect(Collectors.toUnmodifiableSet());
        Optional<List<LottoResult>> after = lottoAutomationPort.awaitLedger(
                session,
                rows -> !newRowsOfRound(keysBefore, rows, plan.round()).isEmpty(),
                VERIFY_TIMEOUT,
                abortRequested
        );
        if (after.isPresent()) {
            List<LottoResult> fresh = newRowsOfRound(keysBefore, after.get(), plan.round());
            int freshCount = toEntries(fresh).stream().mapToInt(LedgerEntry::count).sum();
            if (freshCount != plan.toBuy()) {
                log.warn("[Purchase][{}] New rows count {} differs from requested {}", user.id(), freshCount, plan.toBuy());
            }
            return new PurchaseOutcome.Purchased(plan, captureQuietly(session, fresh), null);
        }

        // 아래 결과들은 원장에 아직 행이 없으므로, 반영 지연 동안 같은 회차 재구매를 막는다.
        if (depositDropped(session, user, depositBefore, plan)) {
            uncertainPurchaseRegistry.record(user.id(), plan.round());
            return new PurchaseOutcome.Purchased(plan, List.of(),
                    "원장 반영이 늦어 예치금 차감으로 구매를 확인했습니다. 티켓은 사이트 구매내역에서 확인하세요.");
        }

        List<String> rejections = submission.dialogMessages().stream()
                .filter(message -> REJECTION_HINTS.stream().anyMatch(message::contains))
                .toList();
        if (!rejections.isEmpty()) {
            uncertainPurchaseRegistry.record(user.id(), plan.round());
            return new PurchaseOutcome.Rejected(plan, rejections);
        }

        return new PurchaseOutcome.Uncertain(plan, uncertainPurchaseRegistry.record(user.id(), plan.round()));
    }

    private boolean depositDropped(
            LottoAutomationSession session,
            LottoUser user,
            OptionalLong depositBefore,
            PurchasePlan plan
    ) {
        if (depositBefore.isEmpty()) {
            return false;
        }
        try {
            OptionalLong depositAfter = Won.parse(lottoAutomationPort.readAccount(session).deposit());
            return depositAfter.isPresent()
                    && depositBefore.getAsLong() - depositAfter.getAsLong() >= plan.requiredDepositWon();
        } catch (RuntimeException exception) {
            log.warn("[Purchase][{}] Deposit re-read failed", user.id(), exception);
            return false;
        }
    }

    /** 구매 판정은 끝났으므로 티켓 캡처 실패는 결과를 바꾸지 않는다. */
    private List<CheckResult> captureQuietly(LottoAutomationSession session, List<LottoResult> rows) {
        try {
            return lottoAutomationPort.captureTickets(session, rows);
        } catch (RuntimeException exception) {
            log.warn("[Purchase] Ticket capture failed - continuing without images", exception);
            return rows.stream().map(row -> new CheckResult(row, null)).toList();
        }
    }

    private List<LottoResult> rowsOfRound(List<LottoResult> rows, int round) {
        return rows.stream().filter(row -> isLottoOfRound(row, round)).toList();
    }

    private List<LottoResult> newRowsOfRound(Set<String> keysBefore, List<LottoResult> rows, int round) {
        return rows.stream()
                .filter(row -> !keysBefore.contains(row.key()))
                .filter(row -> isLottoOfRound(row, round))
                .toList();
    }

    private boolean isLottoOfRound(LottoResult row, int round) {
        LedgerEntry entry = toEntry(row);
        return entry.isLotto645() && entry.round() == round;
    }

    private static List<LedgerEntry> toEntries(List<LottoResult> rows) {
        return rows.stream().map(LottoPurchaseProcedure::toEntry).toList();
    }

    private static LedgerEntry toEntry(LottoResult row) {
        return LedgerEntry.parse(row.key(), row.name(), row.round(), row.count());
    }
}
