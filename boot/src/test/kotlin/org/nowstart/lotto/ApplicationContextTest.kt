package org.nowstart.lotto

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.nowstart.lotto.adapter.outbound.browser.BrowserSettings
import org.nowstart.lotto.adapter.outbound.mail.NotificationMailAdapter
import org.nowstart.lotto.application.port.outbound.SendNotificationPort
import org.nowstart.lotto.application.service.PurchaseProcedure
import org.nowstart.lotto.domain.calendar.DrawCalendar
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles
import tools.jackson.databind.json.JsonMapper

/**
 * 모듈 간 빈 연결을 확인한다(멀티모듈 분리에서 깨지기 쉬운 곳).
 * 계정·메일 값은 가짜이며, local 프로파일은 스케줄러가 꺼져 있고 init=false라 사이트에 접속하지 않는다.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "LOTTO_ID=test-user",
        "LOTTO_PASSWORD=test-password",
        "LOTTO_EMAIL=test@example.com",
        "LOTTO_INIT=false",
        "MAIL_USERNAME=test",
        "MAIL_PASSWORD=test",
        "server.address=127.0.0.1",
        "otel.sdk.disabled=false",
        "otel.traces.exporter=none",
        "otel.metrics.exporter=none",
        "otel.logs.exporter=none",
    ],
)
@ActiveProfiles("local")
class ApplicationContextTest {

    @Autowired
    private lateinit var context: ApplicationContext

    @Test
    fun `local 프로파일로 전체 빈이 연결된다`() {
        assertThat(context.getBean(PurchaseProcedure::class.java)).isNotNull()
        assertThat(context.getBean(SendNotificationPort::class.java)).isInstanceOf(NotificationMailAdapter::class.java)
        assertThat(context.getBean(Clock::class.java).zone).isEqualTo(DrawCalendar.ZONE)
        assertThat(context.getBean(BrowserSettings::class.java).dryRun).isTrue()
    }

    @Test
    fun `OpenTelemetry가 활성화되면 HTTP 필터를 거쳐 OpenAPI 명세를 조회할 수 있다`(@LocalServerPort port: Int) {
        val request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/v3/api-docs"))
            .timeout(Duration.ofSeconds(10))
            .build()
        val response = HttpClient.newHttpClient().use { it.send(request, HttpResponse.BodyHandlers.ofString()) }

        assertThat(response.statusCode()).isEqualTo(200)
        val paths = context.getBean(JsonMapper::class.java).readTree(response.body()).path("paths")
        assertThat(paths.path("/api/lotto/buy").has("post")).isTrue()
    }
}
