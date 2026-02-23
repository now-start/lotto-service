package org.nowstart.lotto.service;

import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.data.properties.LottoProperties;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetryExecutor {

    private final LottoProperties lottoProperties;

    public <T> T execute(String step, Supplier<T> action) {
        int maxRetries = lottoProperties.getMaxRetries();

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("[Retry] step={} attempt={}/{}", step, attempt, maxRetries);
                return action.get();
            } catch (Exception e) {
                if (attempt == maxRetries) {
                    log.error("[Retry] step={} exceeded maxRetries={}", step, maxRetries, e);
                    throw new RuntimeException(step + " 재시도 실패", e);
                }

                long delayMs = lottoProperties.getRetryDelayMs();
                log.warn("[Retry] step={} failed attempt={} waitMs={}", step, attempt, delayMs);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(step + " 재시도 대기 중 인터럽트", interruptedException);
                }
            }
        }

        throw new IllegalStateException("재시도 로직 오류");
    }
}
