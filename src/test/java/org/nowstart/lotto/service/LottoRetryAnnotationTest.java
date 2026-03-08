package org.nowstart.lotto.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Page;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.nowstart.lotto.data.properties.LottoProperties;
import org.springframework.resilience.annotation.Retryable;

class LottoRetryAnnotationTest {

    @Test
    void shouldDeclareRetryOnTransientStepServices() throws NoSuchMethodException {
        assertRetryable(LottoLoginService.class.getMethod("login", Page.class, LottoProperties.User.class));
        assertRetryable(LottoPurchaseService.class.getMethod("buy", Page.class, LottoProperties.User.class));
        assertRetryable(LottoResultService.class.getMethod("check", Page.class));
    }

    private void assertRetryable(Method method) {
        Retryable retryable = method.getAnnotation(Retryable.class);

        assertThat(retryable).isNotNull();
        assertThat(retryable.includes()).containsExactly(Exception.class);
        assertThat(retryable.maxRetriesString()).isEqualTo("${lotto.max-retries:3}");
        assertThat(retryable.delayString()).isEqualTo("${lotto.retry-delay-ms:2000}");
    }
}
