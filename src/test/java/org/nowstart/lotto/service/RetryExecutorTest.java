package org.nowstart.lotto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nowstart.lotto.data.properties.LottoProperties;

@ExtendWith(MockitoExtension.class)
class RetryExecutorTest {

    private RetryExecutor retryExecutor;

    @BeforeEach
    void setUp() {
        LottoProperties lottoProperties = new LottoProperties();
        lottoProperties.setMaxRetries(3);
        lottoProperties.setRetryDelayMs(1);
        retryExecutor = new RetryExecutor(lottoProperties);
    }

    @AfterEach
    void clearInterruptedState() {
        Thread.interrupted();
    }

    @Test
    void shouldRetryAndSucceedAfterTransientFailures() {
        AtomicInteger attempts = new AtomicInteger();

        String result = retryExecutor.execute("login", () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("temporary");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void shouldThrowWhenRetriesExceeded() {
        assertThatThrownBy(() -> retryExecutor.execute("check", () -> {
            throw new IllegalStateException("permanent");
        }))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("재시도 실패");
    }

    @Test
    void shouldStopWhenInterruptedDuringRetryWait() {
        Thread.currentThread().interrupt();
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> retryExecutor.execute("purchase", () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("interrupted");
        }))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("인터럽트");

        assertThat(attempts.get()).isEqualTo(1);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }
}
