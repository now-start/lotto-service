package org.nowstart.lotto;

import static org.assertj.core.api.BDDAssertions.then;

import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nowstart.lotto.adapter.out.mail.NotificationMailAdapter;
import org.nowstart.lotto.application.port.out.SendNotificationPort;
import org.nowstart.lotto.application.service.LottoPurchaseProcedure;
import org.nowstart.lotto.domain.calendar.LottoDrawCalendar;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * 전체 빈 연결을 확인한다(단위 테스트가 잡지 못하는 생성자 주입·@Value·빈 중복 문제).
 * 계정·메일 값은 가짜이며, local 프로파일은 스케줄러가 꺼져 있고 init=false라 사이트에 접속하지 않는다.
 */
@SpringBootTest(properties = {
        "LOTTO_ID=test-user",
        "LOTTO_PASSWORD=test-password",
        "LOTTO_EMAIL=test@example.com",
        "MAIL_USERNAME=test",
        "MAIL_PASSWORD=test",
        "otel.sdk.disabled=true"
})
@ActiveProfiles("local")
@DisplayName("애플리케이션 컨텍스트")
class LottoServiceApplicationContextTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("local 프로파일로 전체 빈이 연결된다")
    void shouldWireApplicationContext() {
        then(context.getBean(LottoPurchaseProcedure.class)).isNotNull();
        then(context.getBean(SendNotificationPort.class)).isInstanceOf(NotificationMailAdapter.class);
        then(context.getBean(Clock.class).getZone()).isEqualTo(LottoDrawCalendar.ZONE);
        then(context.getEnvironment().getProperty("lotto.dry-run")).isEqualTo("true");
    }
}
