package org.nowstart.lotto.domain.exception;

/**
 * 구매내역(원장)을 확실하게 판독하지 못했을 때 던진다.
 * 구매 여부 판단은 이 예외가 나면 진행하지 않는다(fail-closed).
 */
public class LedgerUnreadableException extends RuntimeException {

    public LedgerUnreadableException(String message) {
        super(message);
    }

    public LedgerUnreadableException(String message, Throwable cause) {
        super(message, cause);
    }
}
