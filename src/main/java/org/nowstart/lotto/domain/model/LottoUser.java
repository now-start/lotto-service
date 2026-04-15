package org.nowstart.lotto.domain.model;

public record LottoUser(
        String id,
        String password,
        int count,
        String email,
        boolean init
) {
}
