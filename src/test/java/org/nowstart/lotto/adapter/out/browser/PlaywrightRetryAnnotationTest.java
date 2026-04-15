package org.nowstart.lotto.adapter.out.browser;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Page;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.nowstart.lotto.domain.model.LottoUser;
import org.springframework.resilience.annotation.Retryable;

class PlaywrightRetryAnnotationTest {

    @Test
    void shouldDeclareRetryOnTransientBrowserExecutors() throws NoSuchMethodException {
        assertRetryable(PlaywrightLoginExecutor.class.getMethod("login", Page.class, LottoUser.class));
        assertRetryable(PlaywrightPurchaseExecutor.class.getMethod("buy", Page.class, LottoUser.class));
        assertRetryable(PlaywrightResultExecutor.class.getMethod("check", Page.class));
    }

    private void assertRetryable(Method method) {
        Retryable retryable = method.getAnnotation(Retryable.class);

        assertThat(retryable).isNotNull();
        assertThat(retryable.includes()).containsExactly(Exception.class);
        assertThat(retryable.maxRetriesString()).isEqualTo("${lotto.max-retries:3}");
        assertThat(retryable.delayString()).isEqualTo("${lotto.retry-delay-ms:2000}");
    }
}
