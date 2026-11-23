package org.nowstart.lotto.config;

import java.time.Clock;
import org.nowstart.lotto.application.service.LottoNotificationFactory;
import org.nowstart.lotto.domain.calendar.LottoDrawCalendar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfig {

    @Bean
    public LottoNotificationFactory lottoNotificationFactory() {
        return new LottoNotificationFactory();
    }

    @Bean
    public Clock lottoClock() {
        return Clock.system(LottoDrawCalendar.ZONE);
    }

    @Bean
    public LottoDrawCalendar lottoDrawCalendar(Clock lottoClock) {
        return new LottoDrawCalendar(lottoClock);
    }
}
