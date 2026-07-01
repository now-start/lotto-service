package org.nowstart.lotto.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "lotto")
public class LottoProperties {

    @Valid
    @NotEmpty(message = "최소 1명 이상의 사용자 정보가 필요합니다")
    private List<User> users = new ArrayList<>();

    @Min(value = 1, message = "재시도 횟수는 최소 1회여야 합니다")
    @Max(value = 10, message = "재시도 횟수는 최대 10회까지 가능합니다")
    @NotNull(message = "재시도 횟수는 필수입니다")
    private Integer maxRetries = 3;

    @Min(value = 1000, message = "재시도 지연 시간은 최소 1초여야 합니다")
    @Max(value = 3600000, message = "재시도 지연 시간은 최대 1시간까지 가능합니다")
    @NotNull(message = "재시도 지연 시간은 필수입니다")
    private Integer retryDelayMs = 2000;

    @Min(value = 1000, message = "구매 결과 대기 시간은 최소 1초여야 합니다")
    @Max(value = 300000, message = "구매 결과 대기 시간은 최대 5분까지 가능합니다")
    @NotNull(message = "구매 결과 대기 시간은 필수입니다")
    private Integer purchaseResultTimeoutMs = 30000;

    @Min(value = 100, message = "구매 결과 폴링 간격은 최소 0.1초여야 합니다")
    @Max(value = 10000, message = "구매 결과 폴링 간격은 최대 10초까지 가능합니다")
    @NotNull(message = "구매 결과 폴링 간격은 필수입니다")
    private Integer purchaseResultPollIntervalMs = 1000;

    @Min(value = 1, message = "동시 실행 세션 수는 최소 1이어야 합니다")
    @Max(value = 50, message = "동시 실행 세션 수는 최대 50까지 가능합니다")
    @NotNull(message = "동시 실행 세션 수는 필수입니다")
    private Integer maxConcurrentSessions = 3;

    @Min(value = 1000, message = "사용자 작업 타임아웃은 최소 1초여야 합니다")
    @Max(value = 1800000, message = "사용자 작업 타임아웃은 최대 30분까지 가능합니다")
    @NotNull(message = "사용자 작업 타임아웃은 필수입니다")
    private Integer userTaskTimeoutMs = 180000;

    @Valid
    @NotNull(message = "크론 설정은 필수입니다")
    private Cron cron = new Cron();

    @Data
    @Validated
    public static class User {

        @NotBlank(message = "로또 사이트 아이디는 필수입니다")
        private String id;

        @NotBlank(message = "로또 사이트 비밀번호는 필수입니다")
        private String password;

        @Min(value = 1, message = "구매 수량은 최소 1장이어야 합니다")
        @Max(value = 5, message = "구매 수량은 최대 5장까지 가능합니다")
        @NotNull(message = "구매 수량은 필수입니다")
        private Integer count = 5;

        @Email(message = "올바른 이메일 형식이어야 합니다")
        @NotBlank(message = "알림 수신 이메일은 필수입니다")
        private String email;

        @NotNull(message = "초기화 여부는 필수입니다")
        private Boolean init = false;
    }

    @Data
    @Validated
    public static class Cron {

        @NotBlank(message = "로또 확인 크론 표현식은 필수입니다")
        private String check = "0 0 22 * * 6";

        @NotBlank(message = "로또 구매 크론 표현식은 필수입니다")
        private String buy = "0 0 9 * * 0";

        @NotBlank(message = "스케줄러 타임존은 필수입니다")
        private String zone = "Asia/Seoul";
    }
}
