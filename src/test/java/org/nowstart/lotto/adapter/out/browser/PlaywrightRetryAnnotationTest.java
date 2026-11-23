package org.nowstart.lotto.adapter.out.browser;

import static org.assertj.core.api.BDDAssertions.then;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort.LottoUser;
import org.springframework.resilience.annotation.Retryable;

@DisplayName("Playwright 재시도 애너테이션")
class PlaywrightRetryAnnotationTest {

    @Test
    @DisplayName("조회성 executor(로그인, 결과 확인)는 일시 오류(PlaywrightException)에 한해 재시도를 선언한다")
    void shouldDeclareRetryOnTransientBrowserExecutors() throws NoSuchMethodException {
        assertRetryable(PlaywrightLoginExecutor.class.getMethod("login", Page.class, LottoUser.class));
        assertRetryable(PlaywrightResultExecutor.class.getMethod("check", Page.class));
    }

    @Test
    @DisplayName("구매와 구매 판정용 원장 읽기는 재시도를 선언하지 않는다 (재시도가 브라우저를 오래 붙잡지 않도록)")
    void shouldNotDeclareRetryOnPurchaseAndVerification() throws NoSuchMethodException {
        then(PlaywrightPurchaseExecutor.class
                .getMethod("buy", Page.class, LottoUser.class, int.class, BooleanSupplier.class)
                .getAnnotation(Retryable.class)).isNull();
        then(PlaywrightResultExecutor.class.getMethod("readLedger", Page.class)
                .getAnnotation(Retryable.class)).isNull();
        then(PlaywrightResultExecutor.class
                .getMethod("awaitLedger", Page.class, Predicate.class, Duration.class, BooleanSupplier.class)
                .getAnnotation(Retryable.class)).isNull();
        then(PlaywrightResultExecutor.class.getMethod("captureTickets", Page.class, List.class)
                .getAnnotation(Retryable.class)).isNull();
    }

    private void assertRetryable(Method method) {
        Retryable retryable = method.getAnnotation(Retryable.class);

        then(retryable).isNotNull();
        then(retryable.includes()).containsExactly(PlaywrightException.class);
        then(retryable.maxRetriesString()).isEqualTo("${lotto.max-retries:3}");
        then(retryable.delayString()).isEqualTo("${lotto.retry-delay-ms:2000}");
    }
}
