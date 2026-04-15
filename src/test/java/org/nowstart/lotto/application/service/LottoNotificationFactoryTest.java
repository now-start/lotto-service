package org.nowstart.lotto.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.nowstart.lotto.application.model.LottoCheckResult;
import org.nowstart.lotto.domain.model.LottoAccountSnapshot;
import org.nowstart.lotto.domain.model.LottoResult;
import org.nowstart.lotto.domain.model.LottoUser;
import org.nowstart.lotto.domain.model.NotificationMessage;

class LottoNotificationFactoryTest {

    private final LottoNotificationFactory lottoNotificationFactory = new LottoNotificationFactory();

    @Test
    void shouldIncludePurchasedNumberInSuccessMessage() {
        LottoUser user = new LottoUser("user1", "password", 1, "user1@nowstart.org", false);
        LottoAccountSnapshot accountSnapshot = new LottoAccountSnapshot("홍길동", "5000원");
        LottoCheckResult result = new LottoCheckResult(
                new LottoResult(
                        "2026-04-14",
                        "1234",
                        "로또",
                        "1,2,3,4,5,6",
                        "1",
                        "구매완료",
                        "1000원"
                ),
                new byte[] {1}
        );

        NotificationMessage message = lottoNotificationFactory.createCheckSuccessMessage(
                user,
                accountSnapshot,
                List.of(result)
        ).orElseThrow();

        assertThat(message.text()).contains("번호: 1,2,3,4,5,6");
        assertThat(message.text()).contains("홍길동의 💰예치금 : 5000원");
        assertThat(message.inlineImage()).isEqualTo(new byte[] {1});
    }
}
