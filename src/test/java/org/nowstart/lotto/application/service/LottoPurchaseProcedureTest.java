package org.nowstart.lotto.application.service;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort.LottoUser;
import org.nowstart.lotto.application.port.out.LottoAutomationPort;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.CheckResult;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.LottoAccountSnapshot;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.LottoResult;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.PurchaseSubmission;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.PurchaseSubmission.Status;
import org.nowstart.lotto.application.port.out.LottoAutomationSession;
import org.nowstart.lotto.domain.calendar.LottoDrawCalendar;
import org.nowstart.lotto.domain.exception.LedgerUnreadableException;

@ExtendWith(MockitoExtension.class)
@DisplayName("멱등 구매 절차")
class LottoPurchaseProcedureTest {

    /** 2026-11-23(월) 12:00 KST: 판매 회차 1252. */
    private static final Instant MONDAY_NOON = ZonedDateTime.parse("2026-11-23T12:00:00+09:00[Asia/Seoul]").toInstant();

    @Mock
    private LottoAutomationPort port;

    @Mock
    private LottoAutomationSession session;

    private final MutableClock clock = new MutableClock(MONDAY_NOON);
    private final LottoUser user = new LottoUser("user1", "password", 5, "user1@nowstart.org", false);
    private final LottoAccountSnapshot account = new LottoAccountSnapshot("홍길동", "60,000원");
    private UncertainPurchaseRegistry registry;
    private LottoPurchaseProcedure procedure;

    @BeforeEach
    void setUp() {
        registry = new UncertainPurchaseRegistry(clock);
        procedure = new LottoPurchaseProcedure(port, new LottoDrawCalendar(clock), registry);
    }

    @Test
    @DisplayName("이번 회차를 이미 5장 샀으면 결제 클릭 없이 기존 티켓을 안내한다 (2026-11-23 사례)")
    void shouldNotBuyWhenRoundAlreadyPurchased() {
        LottoResult sunday = lotto("2026-11-22", 1252, 5, "미추첨");
        given(port.readLedger(session)).willReturn(List.of(sunday));
        given(port.captureTickets(session, List.of(sunday))).willReturn(List.of(new CheckResult(sunday, new byte[] {1})));

        PurchaseOutcome outcome = procedure.purchase(session, user, account, () -> false);

        then(outcome).isInstanceOf(PurchaseOutcome.AlreadyPurchased.class);
        PurchaseOutcome.AlreadyPurchased already = (PurchaseOutcome.AlreadyPurchased) outcome;
        then(already.plan().alreadyPurchased()).isEqualTo(5);
        then(already.tickets()).hasSize(1);
        BDDMockito.then(port).should(never()).buy(any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("일부만 샀으면 모자란 수량만 산다")
    void shouldBuyOnlyRemaining() {
        given(port.readLedger(session)).willReturn(List.of(lotto("2026-11-22", 1252, 2, "미추첨")));
        given(port.buy(eq(session), eq(user), eq(3), any())).willReturn(PurchaseSubmission.of(Status.NOT_CLICKED));

        PurchaseOutcome outcome = procedure.purchase(session, user, account, () -> false);

        then(outcome).isInstanceOf(PurchaseOutcome.Aborted.class);
        BDDMockito.then(port).should().buy(eq(session), eq(user), eq(3), any());
    }

    @Test
    @DisplayName("원장이 빈 신규 계정은 설정 수량만큼 산다")
    void shouldBuyFullCountForNewAccount() {
        given(port.readLedger(session)).willReturn(List.of());
        given(port.buy(eq(session), eq(user), eq(5), any())).willReturn(PurchaseSubmission.of(Status.DRY_RUN));

        PurchaseOutcome outcome = procedure.purchase(session, user, account, () -> false);

        then(outcome).isInstanceOf(PurchaseOutcome.DryRun.class);
        BDDMockito.then(port).should(never()).awaitLedger(any(), any(), any(), any());
    }

    @Test
    @DisplayName("다른 게임 행만 있으면 로또는 산 적이 없는 것으로 본다")
    void shouldIgnoreOtherGames() {
        LottoResult pension = new LottoResult("2026-11-20", "290", "연금복권720+", "1조123456", "1", "미추첨", "1,000");
        given(port.readLedger(session)).willReturn(List.of(pension));
        given(port.buy(eq(session), eq(user), eq(5), any())).willReturn(PurchaseSubmission.of(Status.DRY_RUN));

        then(procedure.purchase(session, user, account, () -> false)).isInstanceOf(PurchaseOutcome.DryRun.class);
    }

    @Test
    @DisplayName("예치금이 부족하면 구매 화면에 들어가지 않는다")
    void shouldRefuseWhenDepositInsufficient() {
        given(port.readLedger(session)).willReturn(List.of());

        PurchaseOutcome outcome = procedure.purchase(session, user, new LottoAccountSnapshot("홍길동", "2,000원"), () -> false);

        then(outcome).isInstanceOf(PurchaseOutcome.Refused.class);
        then(((PurchaseOutcome.Refused) outcome).reason()).contains("예치금");
        BDDMockito.then(port).should(never()).buy(any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("원장에 계산보다 큰 회차가 있으면 회차 계산이 틀린 것이므로 구매하지 않는다")
    void shouldRefuseWhenLedgerShowsFutureRound() {
        given(port.readLedger(session)).willReturn(List.of(lotto("2026-11-22", 1253, 1, "미추첨")));

        then(procedure.purchase(session, user, account, () -> false)).isInstanceOf(PurchaseOutcome.Refused.class);
        BDDMockito.then(port).should(never()).buy(any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("원장을 판독하지 못하면 예외를 그대로 올리고 구매하지 않는다(fail-closed)")
    void shouldNotBuyWhenLedgerUnreadable() {
        given(port.readLedger(session)).willThrow(new LedgerUnreadableException("no rows, no marker"));

        thenThrownBy(() -> procedure.purchase(session, user, account, () -> false))
                .isInstanceOf(LedgerUnreadableException.class);
        BDDMockito.then(port).should(never()).buy(any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("확정 클릭 후 원장에 새 행이 보이면 구매 성공이다")
    void shouldConfirmPurchaseByNewLedgerRow() {
        LottoResult fresh = lotto("2026-11-23", 1252, 5, "미추첨");
        given(port.readLedger(session)).willReturn(List.of());
        given(port.buy(eq(session), eq(user), eq(5), any())).willReturn(PurchaseSubmission.of(Status.CLICKED));
        stubLedgerAfterClick(List.of(fresh));
        given(port.captureTickets(session, List.of(fresh))).willReturn(List.of(new CheckResult(fresh, new byte[] {7})));

        PurchaseOutcome outcome = procedure.purchase(session, user, account, () -> false);

        then(outcome).isInstanceOf(PurchaseOutcome.Purchased.class);
        then(((PurchaseOutcome.Purchased) outcome).tickets()).hasSize(1);
        BDDMockito.then(port).should(never()).readAccount(any());
    }

    @Test
    @DisplayName("같은 수량의 기존 행이 있어도 클릭 전에 없던 행만 새 구매로 본다")
    void shouldDistinguishNewRowFromExistingRowWithSameCount() {
        LottoResult morning = lotto("2026-11-23", 1252, 2, "미추첨");
        LottoResult fresh = new LottoResult("2026-11-23", "1252", "로또6/45", "AUTO-2", "2", "미추첨", "2,000");
        LottoUser fourGames = new LottoUser("user1", "password", 4, "user1@nowstart.org", false);
        given(port.readLedger(session)).willReturn(List.of(morning));
        given(port.buy(eq(session), eq(fourGames), eq(2), any())).willReturn(PurchaseSubmission.of(Status.CLICKED));
        stubLedgerAfterClick(List.of(morning, fresh));
        given(port.captureTickets(session, List.of(fresh))).willReturn(List.of(new CheckResult(fresh, null)));

        PurchaseOutcome outcome = procedure.purchase(session, fourGames, account, () -> false);

        then(outcome).isInstanceOf(PurchaseOutcome.Purchased.class);
    }

    @Test
    @DisplayName("원장 반영이 늦어도 예치금이 줄었으면 구매 성공이다")
    void shouldConfirmPurchaseByDepositDrop() {
        given(port.readLedger(session)).willReturn(List.of());
        given(port.buy(eq(session), eq(user), eq(5), any())).willReturn(PurchaseSubmission.of(Status.CLICKED));
        given(port.awaitLedger(eq(session), any(), any(), any())).willReturn(Optional.empty());
        given(port.readAccount(session)).willReturn(new LottoAccountSnapshot("홍길동", "55,000원"));

        PurchaseOutcome outcome = procedure.purchase(session, user, account, () -> false);

        then(outcome).isInstanceOf(PurchaseOutcome.Purchased.class);
        then(((PurchaseOutcome.Purchased) outcome).note()).contains("예치금");
        // 원장에 아직 행이 없으므로 반영 지연 동안 재구매를 막아야 한다
        then(registry.blockedUntil(user.id(), 1252)).isPresent();
    }

    @Test
    @DisplayName("확정 클릭 뒤 판정 중 예외가 나도 '구매 실패'가 아니라 판정 불가로 보고 재구매를 막는다")
    void shouldTreatExceptionAfterClickAsUncertain() {
        given(port.readLedger(session)).willReturn(List.of());
        given(port.buy(eq(session), eq(user), eq(5), any())).willReturn(PurchaseSubmission.of(Status.CLICKED));
        given(port.awaitLedger(eq(session), any(), any(), any()))
                .willThrow(new com.microsoft.playwright.PlaywrightException("Target page, context or browser has been closed"));

        PurchaseOutcome outcome = procedure.purchase(session, user, account, () -> false);

        then(outcome).isInstanceOf(PurchaseOutcome.Uncertain.class);
        then(registry.blockedUntil(user.id(), 1252)).isPresent();
    }

    @Test
    @DisplayName("구매 흔적이 없고 사이트가 거부 문구를 띄웠으면 거부로 본다")
    void shouldTreatSiteRejectionAsRejected() {
        given(port.readLedger(session)).willReturn(List.of());
        given(port.buy(eq(session), eq(user), eq(5), any()))
                .willReturn(new PurchaseSubmission(Status.CLICKED, List.of("이번 회차 구매한도를 초과했습니다")));
        given(port.awaitLedger(eq(session), any(), any(), any())).willReturn(Optional.empty());
        given(port.readAccount(session)).willReturn(account);

        PurchaseOutcome outcome = procedure.purchase(session, user, account, () -> false);

        then(outcome).isInstanceOf(PurchaseOutcome.Rejected.class);
        // 거부 문구 판정이 틀렸을 수도 있으므로 반영 지연 동안은 재구매를 막는다
        then(registry.blockedUntil(user.id(), 1252)).isPresent();
    }

    @Test
    @DisplayName("성공 안내에도 나오는 단어(잔액)만으로는 거부로 보지 않는다")
    void shouldNotTreatGenericBalanceNoticeAsRejection() {
        given(port.readLedger(session)).willReturn(List.of());
        given(port.buy(eq(session), eq(user), eq(5), any()))
                .willReturn(new PurchaseSubmission(Status.CLICKED, List.of("구매가 완료되었습니다. 잔액: 55,000원")));
        given(port.awaitLedger(eq(session), any(), any(), any())).willReturn(Optional.empty());
        given(port.readAccount(session)).willReturn(account);

        then(procedure.purchase(session, user, account, () -> false)).isInstanceOf(PurchaseOutcome.Uncertain.class);
    }

    @Test
    @DisplayName("증거가 없으면 판정 불가로 보고, 원장 반영 대기 시간 동안 같은 회차 재구매를 막는다")
    void shouldBlockRepurchaseWhileUncertain() {
        given(port.readLedger(session)).willReturn(List.of());
        given(port.buy(eq(session), eq(user), eq(5), any())).willReturn(PurchaseSubmission.of(Status.CLICKED));
        given(port.awaitLedger(eq(session), any(), any(), any())).willReturn(Optional.empty());
        given(port.readAccount(session)).willReturn(account);

        PurchaseOutcome outcome = procedure.purchase(session, user, account, () -> false);

        then(outcome).isInstanceOf(PurchaseOutcome.Uncertain.class);
        then(procedure.precheck(user)).get().isInstanceOf(PurchaseOutcome.Blocked.class);

        clock.advance(UncertainPurchaseRegistry.LEDGER_LAG);
        then(procedure.precheck(user)).isEmpty();
    }

    @Test
    @DisplayName("판매 시간이 아니면 원장도 읽지 않고 다음 판매 시각을 알려 준다")
    void shouldReportNotOnSaleWithoutTouchingBrowser() {
        clock.set(ZonedDateTime.parse("2026-11-28T20:30:00+09:00[Asia/Seoul]").toInstant());

        PurchaseOutcome outcome = procedure.purchase(session, user, account, () -> false);

        then(outcome).isInstanceOf(PurchaseOutcome.NotOnSale.class);
        PurchaseOutcome.NotOnSale notOnSale = (PurchaseOutcome.NotOnSale) outcome;
        then(notOnSale.nextRound()).isEqualTo(1253);
        then(notOnSale.nextPurchasableAt()).isEqualTo(ZonedDateTime.parse("2026-11-29T06:00:00+09:00[Asia/Seoul]"));
        BDDMockito.then(port).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("구매가 확인된 뒤 티켓 캡처가 실패해도 결과는 구매 성공이다")
    void shouldStaySucceededWhenTicketCaptureFails() {
        LottoResult fresh = lotto("2026-11-23", 1252, 5, "미추첨");
        given(port.readLedger(session)).willReturn(List.of());
        given(port.buy(eq(session), eq(user), eq(5), any())).willReturn(PurchaseSubmission.of(Status.CLICKED));
        stubLedgerAfterClick(List.of(fresh));
        given(port.captureTickets(eq(session), anyList())).willThrow(new IllegalStateException("modal timeout"));

        PurchaseOutcome outcome = procedure.purchase(session, user, account, () -> false);

        then(outcome).isInstanceOf(PurchaseOutcome.Purchased.class);
        then(((PurchaseOutcome.Purchased) outcome).tickets()).singleElement()
                .satisfies(ticket -> then(ticket.detailImage()).isNull());
    }

    private void stubLedgerAfterClick(List<LottoResult> after) {
        given(port.awaitLedger(eq(session), any(), any(), any())).willAnswer(invocation -> {
            Predicate<List<LottoResult>> condition = invocation.getArgument(1);
            return condition.test(new ArrayList<>(after)) ? Optional.of(after) : Optional.empty();
        });
    }

    private static LottoResult lotto(String date, int round, int count, String status) {
        return new LottoResult(date, String.valueOf(round), "로또6/45", "AUTO-" + date + "-" + count,
                String.valueOf(count), status, String.format("%,d", count * 1000));
    }

    static final class MutableClock extends Clock {

        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return LottoDrawCalendar.ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
