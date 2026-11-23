package org.nowstart.lotto.domain.ledger;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nowstart.lotto.domain.exception.LedgerUnreadableException;

@DisplayName("원장 계산")
class LedgerMathTest {

    @Test
    @DisplayName("회차별 구매 수량은 로또6/45 행의 수량 합이고 상태와 무관하다")
    void shouldSumLottoCountsOfRound() {
        List<LedgerEntry> entries = List.of(
                LedgerEntry.parse("a", "로또6/45", "1252", "2"),
                LedgerEntry.parse("b", "로또6/45", "1252", "1"),
                LedgerEntry.parse("c", "로또6/45", "1251", "5"),
                LedgerEntry.parse("d", "연금복권720+", "-", "-")
        );

        then(LedgerMath.purchasedCount(entries, 1252)).isEqualTo(3);
        then(LedgerMath.purchasedCount(entries, 1253)).isZero();
        then(LedgerMath.maxRound(entries)).hasValue(1252);
        then(LedgerMath.entriesOfRound(entries, 1252)).extracting(LedgerEntry::key).containsExactly("a", "b");
    }

    @Test
    @DisplayName("다른 게임 행만 있으면 로또 구매 수량은 0이다")
    void shouldIgnoreOtherGames() {
        List<LedgerEntry> entries = List.of(LedgerEntry.parse("p", "연금복권720+", "290", "1"));

        then(LedgerMath.purchasedCount(entries, 290)).isZero();
        then(LedgerMath.maxRound(entries)).isEmpty();
    }

    @Test
    @DisplayName("클릭 후 새 행은 클릭 전 키에 없던 해당 회차 행만이다 (같은 수량 행이 이미 있어도 구분)")
    void shouldFindNewEntriesBySetDifference() {
        LedgerEntry existing = LedgerEntry.parse("old", "로또6/45", "1252", "2");
        LedgerEntry fresh = LedgerEntry.parse("new", "로또6/45", "1252", "2");
        LedgerEntry otherRound = LedgerEntry.parse("prev", "로또6/45", "1251", "2");

        List<LedgerEntry> result = LedgerMath.newEntries(Set.of("old", "prev"), List.of(existing, fresh, otherRound), 1252);

        then(result).containsExactly(fresh);
    }

    @Test
    @DisplayName("로또6/45 행의 회차나 수량을 해석하지 못하면 판독 불가 예외를 던진다")
    void shouldFailClosedWhenLottoRowIsUnparsable() {
        thenThrownBy(() -> LedgerEntry.parse("x", "로또6/45", "회차없음", "5"))
                .isInstanceOf(LedgerUnreadableException.class);
        thenThrownBy(() -> LedgerEntry.parse("x", "로또 6/45", "1252", ""))
                .isInstanceOf(LedgerUnreadableException.class);
    }
}
