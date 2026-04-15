package org.nowstart.lotto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableResilientMethods
@ConfigurationPropertiesScan
public class LottoServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LottoServiceApplication.class, args);
    }
}
