package org.nowstart.lotto.config;

import org.nowstart.lotto.application.service.LottoNotificationFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfig {

    @Bean
    public LottoNotificationFactory lottoNotificationFactory() {
        return new LottoNotificationFactory();
    }
}
