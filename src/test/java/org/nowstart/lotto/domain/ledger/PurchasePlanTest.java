package org.nowstart.lotto.domain.ledger;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

@DisplayName("회차 구매 계획")
class PurchasePlanTest {

    @ParameterizedTest(name = "목표 {0}, 이미 {1} → 이번 {2}")
    @CsvSource({
            "5, 0, 5",   // 신규 계정(빈 원장)
            "5, 2, 3",   // 일부만 산 회차는 모자란 수량만
            "5, 5, 0",   // 이미 다 샀으면 사지 않음 (2026-11-23 사례)
            "3, 5, 0",
            "1, 0, 1",
            "5, 7, 0"    // 원장이 한도보다 많아도 음수가 되지 않음
    })
    void shouldBuyOnlyRemainingWithinRoundLimit(int target, int already, int toBuy) {
        PurchasePlan plan = PurchasePlan.of(1252, target, already);

        then(plan.toBuy()).isEqualTo(toBuy);
        then(plan.isComplete()).isEqualTo(toBuy == 0);
        then(plan.requiredDepositWon()).isEqualTo(toBuy * 1_000L);
    }

    @Test
    @DisplayName("잘못된 입력은 거부한다")
    void shouldRejectInvalidInput() {
        thenThrownBy(() -> PurchasePlan.of(0, 5, 0)).isInstanceOf(IllegalArgumentException.class);
        thenThrownBy(() -> PurchasePlan.of(1252, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        thenThrownBy(() -> PurchasePlan.of(1252, 5, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("금액 문자열을 원 단위로 해석한다")
    void shouldParseWon() {
        then(Won.parse("60,000원")).hasValue(60_000L);
        then(Won.parse("108,818")).hasValue(108_818L);
        then(Won.parse("")).isEmpty();
        then(Won.parse(null)).isEmpty();
    }
}
