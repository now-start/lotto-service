package org.nowstart.lotto.config;

import com.microsoft.playwright.BrowserType;
import java.util.function.Supplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlaywrightConfig {

    @Bean
    public Supplier<BrowserType.LaunchOptions> browserLaunchOptions() {
        return () -> new BrowserType.LaunchOptions().setHeadless(true);
    }
}
