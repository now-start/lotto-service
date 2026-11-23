package org.nowstart.lotto.domain.ledger;

/**
 * 한 회차의 구매 계획. 회차당 인터넷 구매 한도(5게임)와 이미 산 수량을 반영해 이번에 살 수량을 정한다.
 *
 * @param round            대상 회차
 * @param targetCount      설정된 목표 수량
 * @param alreadyPurchased 원장 기준 이미 산 수량
 * @param toBuy            이번에 살 수량(0이면 구매하지 않음)
 */
public record PurchasePlan(int round, int targetCount, int alreadyPurchased, int toBuy) {

    public static final int MAX_GAMES_PER_ROUND = 5;
    public static final long PRICE_PER_GAME_WON = 1_000L;

    public static PurchasePlan of(int round, int targetCount, int alreadyPurchased) {
        if (round < 1) {
            throw new IllegalArgumentException("round must be positive: " + round);
        }
        if (targetCount < 1) {
            throw new IllegalArgumentException("targetCount must be positive: " + targetCount);
        }
        if (alreadyPurchased < 0) {
            throw new IllegalArgumentException("alreadyPurchased must not be negative: " + alreadyPurchased);
        }
        int target = Math.min(targetCount, MAX_GAMES_PER_ROUND);
        return new PurchasePlan(round, targetCount, alreadyPurchased, Math.max(0, target - alreadyPurchased));
    }

    public boolean isComplete() {
        return toBuy == 0;
    }

    public long requiredDepositWon() {
        return toBuy * PRICE_PER_GAME_WON;
    }
}
