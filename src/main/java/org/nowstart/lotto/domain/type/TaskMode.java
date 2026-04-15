package org.nowstart.lotto.domain.type;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum TaskMode {
    CHECK("⚠️로또 확인 실패⚠️"),
    PURCHASE("⚠️로또 구매 실패⚠️");

    private final String failureSubject;

    public String getFailureSubject() {
        return failureSubject;
    }
}
