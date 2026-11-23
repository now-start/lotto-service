package org.nowstart.lotto.domain.ledger;

import java.util.Collection;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

/** 원장 행으로 구매 수량을 계산한다. 로또6/45 행만 대상으로 한다. */
public final class LedgerMath {

    private LedgerMath() {
    }

    /** 해당 회차에 이미 구매한 수량 합. 상태(미추첨 등)는 보지 않는다. */
    public static int purchasedCount(Collection<LedgerEntry> entries, int round) {
        return entries.stream()
                .filter(LedgerEntry::isLotto645)
                .filter(entry -> entry.round() == round)
                .mapToInt(LedgerEntry::count)
                .sum();
    }

    /** 원장에 보이는 가장 큰 로또6/45 회차. */
    public static OptionalInt maxRound(Collection<LedgerEntry> entries) {
        return entries.stream()
                .filter(LedgerEntry::isLotto645)
                .mapToInt(LedgerEntry::round)
                .max();
    }

    /** 클릭 전에 없던 해당 회차의 로또6/45 행. */
    public static List<LedgerEntry> newEntries(Set<String> keysBefore, Collection<LedgerEntry> after, int round) {
        return after.stream()
                .filter(LedgerEntry::isLotto645)
                .filter(entry -> entry.round() == round)
                .filter(entry -> !keysBefore.contains(entry.key()))
                .toList();
    }

    public static List<LedgerEntry> entriesOfRound(Collection<LedgerEntry> entries, int round) {
        return entries.stream()
                .filter(LedgerEntry::isLotto645)
                .filter(entry -> entry.round() == round)
                .toList();
    }
}
