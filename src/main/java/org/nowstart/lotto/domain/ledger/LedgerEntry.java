package org.nowstart.lotto.domain.ledger;

import java.util.Objects;
import org.nowstart.lotto.domain.exception.LedgerUnreadableException;

/**
 * 구매내역(원장) 한 행에서 구매 판단에 필요한 값만 담는다.
 *
 * @param key      행을 식별하는 문자열(클릭 전후 비교용)
 * @param gameName 복권명
 * @param round    회차. 로또6/45가 아닌 행은 0
 * @param count    구매 수량. 로또6/45가 아닌 행은 0
 */
public record LedgerEntry(String key, String gameName, int round, int count) {

    public static final String LOTTO_645 = "로또6/45";

    public LedgerEntry {
        Objects.requireNonNull(key, "key must not be null");
        gameName = gameName == null ? "" : gameName.trim();
    }

    /**
     * 원장 행 문자열을 해석한다. 로또6/45 행의 회차·수량이 숫자가 아니면 판독 불가로 본다.
     * 다른 게임 행은 구매 판단에 쓰지 않으므로 숫자 해석을 요구하지 않는다.
     */
    public static LedgerEntry parse(String key, String gameName, String roundText, String countText) {
        if (!isLotto645Name(gameName)) {
            return new LedgerEntry(key, gameName, 0, 0);
        }
        return new LedgerEntry(key, gameName, parsePositive("회차", roundText), parsePositive("수량", countText));
    }

    public boolean isLotto645() {
        return isLotto645Name(gameName);
    }

    private static boolean isLotto645Name(String gameName) {
        return gameName != null && gameName.replaceAll("\\s+", "").contains(LOTTO_645);
    }

    private static int parsePositive(String field, String text) {
        String digits = text == null ? "" : text.replaceAll("\\D", "");
        if (digits.isEmpty() || digits.length() > 9) {
            throw new LedgerUnreadableException("로또6/45 원장 행의 " + field + "를 해석하지 못했습니다: '" + text + "'");
        }
        int value = Integer.parseInt(digits);
        if (value <= 0) {
            throw new LedgerUnreadableException("로또6/45 원장 행의 " + field + "가 0 이하입니다: '" + text + "'");
        }
        return value;
    }
}
