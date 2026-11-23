package org.nowstart.lotto

import java.time.Clock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.nowstart.lotto.adapter.outbound.browser.BrowserSettings
import org.nowstart.lotto.adapter.outbound.mail.NotificationMailAdapter
import org.nowstart.lotto.application.port.outbound.SendNotificationPort
import org.nowstart.lotto.application.service.LottoPurchaseProcedure
import org.nowstart.lotto.domain.calendar.LottoDrawCalendar
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles

/**
 * 모듈 간 빈 연결을 확인한다(멀티모듈 분리에서 깨지기 쉬운 곳).
 * 계정·메일 값은 가짜이며, local 프로파일은 스케줄러가 꺼져 있고 init=false라 사이트에 접속하지 않는다.
 */
@SpringBootTest(
    properties = [
        "LOTTO_ID=test-user",
        "LOTTO_PASSWORD=test-password",
        "LOTTO_EMAIL=test@example.com",
        "MAIL_USERNAME=test",
        "MAIL_PASSWORD=test",
        "otel.sdk.disabled=true",
    ],
)
@ActiveProfiles("local")
class LottoServiceApplicationContextTest {

    @Autowired
    private lateinit var context: ApplicationContext

    @Test
    fun `local 프로파일로 전체 빈이 연결된다`() {
        assertThat(context.getBean(LottoPurchaseProcedure::class.java)).isNotNull()
        assertThat(context.getBean(SendNotificationPort::class.java)).isInstanceOf(NotificationMailAdapter::class.java)
        assertThat(context.getBean(Clock::class.java).zone).isEqualTo(LottoDrawCalendar.ZONE)
        assertThat(context.getBean(BrowserSettings::class.java).dryRun).isTrue()
    }
}
