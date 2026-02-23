package org.nowstart.lotto.data.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TaskMode {
    CHECK_ONLY(false, "⚠️로또 확인 실패⚠️"),
    BUY_AND_CHECK(true, "⚠️로또 구매 실패⚠️");

    private final boolean buyEnabled;
    private final String failureSubject;
}
