package org.nowstart.lotto.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import java.time.Clock
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.nowstart.lotto.adapter.outbound.browser.BrowserSettings
import org.nowstart.lotto.application.service.NotificationFactory
import org.nowstart.lotto.domain.calendar.DrawCalendar
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** 모듈별 설정 레코드와 공용 빈을 만든다. 각 모듈은 AutomationProperties 구조를 몰라도 된다. */
@Configuration
class ApplicationConfig {

    @Bean
    fun notificationFactory() = NotificationFactory()

    @Bean
    fun clock(): Clock = Clock.system(DrawCalendar.ZONE)

    @Bean
    fun drawCalendar(clock: Clock) = DrawCalendar(clock)

    @Bean
    fun browserSettings(properties: AutomationProperties) = BrowserSettings(dryRun = properties.dryRun)

    @Bean(name = ["jobExecutor"], destroyMethod = "shutdownNow")
    fun jobExecutor(): ExecutorService = Executors.newVirtualThreadPerTaskExecutor()
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
