package org.nowstart.lotto.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AsyncConfig {

    @Bean(name = "lottoTaskExecutor", destroyMethod = "shutdownNow")
    public ExecutorService lottoTaskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
