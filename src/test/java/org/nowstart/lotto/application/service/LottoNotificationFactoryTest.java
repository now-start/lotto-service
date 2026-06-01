package org.nowstart.lotto.application.service;

import static org.assertj.core.api.BDDAssertions.then;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.CheckResult;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.LottoAccountSnapshot;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.LottoResult;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort.LottoUser;
import org.nowstart.lotto.application.port.out.SendNotificationPort.NotificationMessage;

@DisplayName("로또 알림 생성")
class LottoNotificationFactoryTest {

    private final LottoNotificationFactory lottoNotificationFactory = new LottoNotificationFactory();

    @Test
    @DisplayName("확인 성공 알림에 구매 번호와 예치금을 포함한다")
    void shouldIncludePurchasedNumberInSuccessMessage() {
        // 준비: 사용자, 계좌 스냅샷, 미추첨 결과가 있다
        LottoUser user = new LottoUser("user1", "password", 1, "user1@nowstart.org", false);
        LottoAccountSnapshot accountSnapshot = new LottoAccountSnapshot("홍길동", "5000원");
        CheckResult result = new CheckResult(
                new LottoResult(
                        "2026-04-14",
                        "1234",
                        "로또",
                        "1,2,3,4,5,6",
                        "1",
                        "미추첨",
                        "1000원"
                ),
                new byte[] {1}
        );

        // 실행: 확인 성공 알림을 생성한다
        NotificationMessage message = lottoNotificationFactory.createCheckSuccessMessage(
                user,
                accountSnapshot,
                List.of(result)
        ).orElseThrow();

        // 검증: 알림 본문과 이미지가 결과 정보를 담고 있다
        then(message.text()).contains("번호: 1,2,3,4,5,6");
        then(message.text()).contains("홍길동의 💰예치금 : 5000원");
        then(message.inlineImage()).isEqualTo(new byte[] {1});
    }

    @Test
    @DisplayName("여러 결과가 있으면 가장 최신 회차를 알림 대상으로 선택한다")
    void shouldSelectLatestResultByRound() {
        // 준비: 서로 다른 회차의 확인 결과가 있다
        LottoUser user = new LottoUser("user1", "password", 1, "user1@nowstart.org", false);
        LottoAccountSnapshot accountSnapshot = new LottoAccountSnapshot("홍길동", "5000원");
        CheckResult olderResult = new CheckResult(
                new LottoResult(
                        "2026-04-13",
                        "1233",
                        "로또",
                        "1,2,3,4,5,6",
                        "1",
                        "미추첨",
                        "1000원"
                ),
                new byte[] {1}
        );
        CheckResult latestResult = new CheckResult(
                new LottoResult(
                        "2026-04-12",
                        "1234",
                        "로또",
                        "7,8,9,10,11,12",
                        "1",
                        "미추첨",
                        "1000원"
                ),
                new byte[] {2}
        );

        // 실행: 확인 성공 알림을 생성한다
        NotificationMessage message = lottoNotificationFactory.createCheckSuccessMessage(
                user,
                accountSnapshot,
                List.of(olderResult, latestResult)
        ).orElseThrow();

        // 검증: 가장 최신 회차와 해당 번호, 이미지가 사용된다
        then(message.subject()).contains("1234회차");
        then(message.text()).contains("회차: 1234");
        then(message.text()).contains("번호: 7,8,9,10,11,12");
        then(message.inlineImage()).isEqualTo(new byte[] {2});
    }
}
