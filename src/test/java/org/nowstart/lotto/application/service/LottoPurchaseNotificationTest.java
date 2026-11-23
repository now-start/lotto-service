package org.nowstart.lotto.application.service;

import static org.assertj.core.api.BDDAssertions.then;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort.LottoUser;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.CheckResult;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.LottoAccountSnapshot;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.LottoResult;
import org.nowstart.lotto.application.port.out.SendNotificationPort.NotificationMessage;
import org.nowstart.lotto.domain.ledger.PurchasePlan;

@DisplayName("구매 결과 알림")
class LottoPurchaseNotificationTest {

    private final LottoNotificationFactory factory = new LottoNotificationFactory();
    private final LottoUser user = new LottoUser("user1", "password", 5, "user1@nowstart.org", false);
    private final LottoAccountSnapshot account = new LottoAccountSnapshot("홍길동", "60,000원");
    private final LottoResult ticket = new LottoResult("2026-11-22", "1252", "로또6/45", "A", "5", "미추첨", "5,000");

    @Test
    @DisplayName("이미 구매했으면 회차와 기존 티켓 이미지를 담아 알린다")
    void shouldDescribeAlreadyPurchased() {
        var outcome = new PurchaseOutcome.AlreadyPurchased(PurchasePlan.of(1252, 5, 5),
                List.of(new CheckResult(ticket, new byte[] {1})));

        NotificationMessage message = factory.createPurchaseOutcomeMessage(user, account, outcome).orElseThrow();

        then(message.subject()).isEqualTo("[user1] ✅로또6/45 1252회차 이미 구매됨");
        then(message.text()).contains("이미 5장 구매되어 추가로 구매하지 않았습니다");
        then(message.hasInlineImage()).isTrue();
        then(message.to()).isEqualTo("user1@nowstart.org");
    }

    @Test
    @DisplayName("판정 불가는 '구매 실패'가 아니라 수동 구매 금지 안내로 알린다")
    void shouldWarnAgainstManualPurchaseWhenUncertain() {
        var outcome = new PurchaseOutcome.Uncertain(PurchasePlan.of(1252, 5, 0), Instant.parse("2026-11-23T02:40:00Z"));

        NotificationMessage message = factory.createPurchaseOutcomeMessage(user, account, outcome).orElseThrow();

        then(message.subject()).contains("수동 구매 금지").doesNotContain("실패");
        then(message.text()).contains("2026-11-23 11:40").contains("다시 구매하지 마세요");
    }

    @Test
    @DisplayName("판매 시간이 아니면 다음 구매 가능 시각과 회차를 알린다")
    void shouldDescribeNextSalesWindow() {
        var outcome = new PurchaseOutcome.NotOnSale(ZonedDateTime.parse("2026-11-29T06:00:00+09:00[Asia/Seoul]"), 1253);

        NotificationMessage message = factory.createPurchaseOutcomeMessage(user, account, outcome).orElseThrow();

        then(message.text()).contains("2026-11-29 06:00 (1253회차)");
    }

    @Test
    @DisplayName("티켓 이미지 없이 예치금으로 확인한 구매도 성공으로 알린다")
    void shouldDescribePurchaseConfirmedByDeposit() {
        var outcome = new PurchaseOutcome.Purchased(PurchasePlan.of(1252, 5, 0), List.of(), "예치금 차감으로 확인");

        NotificationMessage message = factory.createPurchaseOutcomeMessage(user, account, outcome).orElseThrow();

        then(message.subject()).isEqualTo("[user1] ✅로또6/45 1252회차 구매 완료");
        then(message.text()).contains("구매 수량: 5장").contains("예치금 차감으로 확인");
    }

    @Test
    @DisplayName("dry-run과 중단은 이 경로로 알리지 않는다")
    void shouldNotNotifyDryRunOrAbort() {
        then(factory.createPurchaseOutcomeMessage(user, account, new PurchaseOutcome.DryRun(PurchasePlan.of(1252, 5, 0))))
                .isEmpty();
        then(factory.createPurchaseOutcomeMessage(user, account, new PurchaseOutcome.Aborted("최종 확정 전"))).isEmpty();
    }
}
