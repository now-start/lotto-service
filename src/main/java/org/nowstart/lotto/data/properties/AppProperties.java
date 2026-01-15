package org.nowstart.lotto.data.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "lotto")
public class AppProperties {

    private Integer maxRetries = 3;

    private Integer retryDelayMs = 2000;

    @Valid
    @NotNull(message = "크론 설정은 필수입니다")
    private Cron cron = new Cron();

    @Data
    @Validated
    public static class Cron {

        @NotBlank(message = "로또 확인 크론 표현식은 필수입니다")
        private String check;

        @NotBlank(message = "로또 구매 크론 표현식은 필수입니다")
        private String buy;
    }
}