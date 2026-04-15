package org.nowstart.lotto.domain.model;

public record LottoAccountSnapshot(
        String name,
        String deposit
) {
    public String asNotificationText() {
        return name + "의 💰예치금 : " + deposit;
    }
}
