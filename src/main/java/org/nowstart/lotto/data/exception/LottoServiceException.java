package org.nowstart.lotto.data.exception;

public class LottoServiceException extends RuntimeException {

    public LottoServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public static class WebAutomationException extends LottoServiceException {
        public WebAutomationException(String message, Throwable cause) {
            super("웹 자동화 실패: " + message, cause);
        }
    }

    public static class PageInitializationException extends LottoServiceException {
        public PageInitializationException(String message, Throwable cause) {
            super("페이지 초기화 실패: " + message, cause);
        }
    }
}