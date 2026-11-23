package org.nowstart.lotto.domain.ledger;

import java.util.OptionalLong;

/** 화면에 표시된 금액 문자열("60,000원")을 원 단위 정수로 바꾼다. */
public final class Won {

    private Won() {
    }

    public static OptionalLong parse(String text) {
        String digits = text == null ? "" : text.replaceAll("\\D", "");
        if (digits.isEmpty() || digits.length() > 15) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Long.parseLong(digits));
    }
}
