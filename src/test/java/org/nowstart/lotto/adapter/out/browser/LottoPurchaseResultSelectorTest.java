package org.nowstart.lotto.adapter.out.browser;

import static org.assertj.core.api.BDDAssertions.then;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.PurchaseReceipt;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.LottoResult;

@DisplayName("구매 결과 선택")
class LottoPurchaseResultSelectorTest {

    private static final LocalDate PURCHASE_DATE = LocalDate.of(2026, 5, 30);

    @Test
    @DisplayName("구매 수량과 미추첨 상태가 맞는 최신 회차를 선택한다")
    void shouldSelectLatestPendingResultThatMatchesPurchasedCount() {
        // 준비: 지난 회차 낙첨 결과와 이번 회차 미추첨 결과가 함께 있다
        LottoResult previousWeek = result("2026-05-23", "1170", "1,2,3,4,5,6", "낙첨");
        LottoResult newPurchase = result("2026-05-30", "1171", "7,8,9,10,11,12", "미추첨");

        // 실행: 구매 수량 1장 기준으로 새 구매 결과를 고른다
        var selected = LottoPurchaseResultSelector.selectNewPurchase(
                List.of(previousWeek, newPurchase),
                new PurchaseReceipt(1, PURCHASE_DATE)
        );

        // 검증: 이번 회차 미추첨 결과가 선택된다
        then(selected).contains(newPurchase);
    }

    @Test
    @DisplayName("새 미추첨 결과가 없으면 지난 완료 결과를 선택하지 않는다")
    void shouldNotSelectPreviousCompletedResultWhenFreshPurchaseIsMissing() {
        // 준비: 지난 회차 낙첨 결과만 있다
        LottoResult previousWeek = result("2026-05-23", "1170", "1,2,3,4,5,6", "낙첨");

        // 실행: 구매 수량 1장 기준으로 새 구매 결과를 고른다
        var selected = LottoPurchaseResultSelector.selectNewPurchase(
                List.of(previousWeek),
                new PurchaseReceipt(1, PURCHASE_DATE)
        );

        // 검증: 선택 결과가 없다
        then(selected).isEmpty();
    }

    @Test
    @DisplayName("구매 수량이 다르면 미추첨 결과라도 선택하지 않는다")
    void shouldIgnoreNewRowsThatDoNotMatchPurchasedCount() {
        // 준비: 구매 수량과 다른 2장짜리 미추첨 결과가 있다
        LottoResult newRowWithDifferentCount = new LottoResult(
                "2026-05-30",
                "1171",
                "로또",
                "7,8,9,10,11,12",
                "2",
                "미추첨",
                "2000원"
        );

        // 실행: 구매 수량 1장 기준으로 새 구매 결과를 고른다
        var selected = LottoPurchaseResultSelector.selectNewPurchase(
                List.of(newRowWithDifferentCount),
                new PurchaseReceipt(1, PURCHASE_DATE)
        );

        // 검증: 수량이 맞지 않아 선택하지 않는다
        then(selected).isEmpty();
    }

    @Test
    @DisplayName("정의되지 않은 구매완료 상태는 선택하지 않는다")
    void shouldIgnoreUnknownPurchaseCompletedStatus() {
        // 준비: MessageType에 없는 구매완료 상태 결과가 있다
        LottoResult unknownStatus = result("2026-05-30", "1171", "7,8,9,10,11,12", "구매완료");

        // 실행: 구매 수량 1장 기준으로 새 구매 결과를 고른다
        var selected = LottoPurchaseResultSelector.selectNewPurchase(
                List.of(unknownStatus),
                new PurchaseReceipt(1, PURCHASE_DATE)
        );

        // 검증: 정의된 미추첨 상태가 아니므로 선택하지 않는다
        then(selected).isEmpty();
    }

    @Test
    @DisplayName("같은 회차와 수량이라도 구매일이 다르면 선택하지 않는다")
    void shouldIgnorePendingRowsFromDifferentPurchaseDate() {
        // 준비: 같은 회차와 수량의 미추첨 결과가 서로 다른 날짜로 존재한다
        LottoResult staleRow = result("2026.5.29", "1171", "1,2,3,4,5,6", "미추첨");
        LottoResult purchasedToday = result("2026.5.30", "1171", "7,8,9,10,11,12", "미추첨");

        // 실행: 구매일 2026-05-30 기준으로 새 구매 결과를 고른다
        var selected = LottoPurchaseResultSelector.selectNewPurchase(
                List.of(staleRow, purchasedToday),
                new PurchaseReceipt(1, PURCHASE_DATE)
        );

        // 검증: 구매일이 맞는 행만 선택된다
        then(selected).contains(purchasedToday);
    }

    private LottoResult result(String date, String round, String number, String result) {
        return new LottoResult(date, round, "로또", number, "1", result, "1000원");
    }
}
