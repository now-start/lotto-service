package org.nowstart.lotto.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import java.time.Clock
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.nowstart.lotto.adapter.outbound.browser.BrowserSettings
import org.nowstart.lotto.application.service.ExecutionSettings
import org.nowstart.lotto.application.service.LottoNotificationFactory
import org.nowstart.lotto.domain.calendar.LottoDrawCalendar
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** 모듈별 설정 레코드와 공용 빈을 만든다. 각 모듈은 LottoProperties 구조를 몰라도 된다. */
@Configuration
class ApplicationConfig {

    @Bean
    fun lottoNotificationFactory() = LottoNotificationFactory()

    @Bean
    fun lottoClock(): Clock = Clock.system(LottoDrawCalendar.ZONE)

    @Bean
    fun lottoDrawCalendar(lottoClock: Clock) = LottoDrawCalendar(lottoClock)

    @Bean
    fun executionSettings(properties: LottoProperties) = ExecutionSettings(properties.userTaskTimeoutMs.toLong())

    @Bean
    fun browserSettings(properties: LottoProperties) = BrowserSettings(
        maxConcurrentSessions = properties.maxConcurrentSessions,
        sessionPermitTimeoutMs = properties.userTaskTimeoutMs.toLong(),
        dryRun = properties.dryRun,
    )

    @Bean(name = ["lottoTaskExecutor"], destroyMethod = "shutdownNow")
    fun lottoTaskExecutor(): ExecutorService = Executors.newVirtualThreadPerTaskExecutor()
}

@Configuration
class SwaggerConfig(private val buildProperties: BuildProperties) {

    @Bean
    fun openAPI(): OpenAPI = OpenAPI().info(
        Info()
            .title("Lotto Service API")
            .description("로또 서비스 API 문서")
            .version(buildProperties.version)
            .contact(Contact().name("Lotto Service Team").email("support@nowstart.org"))
            .license(License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0.html")),
    )
}
