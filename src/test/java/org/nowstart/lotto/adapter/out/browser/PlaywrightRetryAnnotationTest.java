package org.nowstart.lotto.adapter.out.browser;

import static org.assertj.core.api.BDDAssertions.then;

import com.microsoft.playwright.Page;
import java.util.function.BooleanSupplier;
import com.microsoft.playwright.PlaywrightException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.PurchaseReceipt;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort.LottoUser;
import org.springframework.resilience.annotation.Retryable;

@DisplayName("Playwright 재시도 애너테이션")
class PlaywrightRetryAnnotationTest {

    @Test
    @DisplayName("브라우저 조회 executor는 일시 오류(PlaywrightException)에 한해 재시도를 선언한다")
    void shouldDeclareRetryOnTransientBrowserExecutors() throws NoSuchMethodException {
        // 준비 및 실행/검증: 조회성(멱등) executor는 동일한 재시도 정책을 가진다
        assertRetryable(PlaywrightLoginExecutor.class.getMethod("login", Page.class, LottoUser.class));
        assertRetryable(PlaywrightResultExecutor.class.getMethod("check", Page.class));
        assertRetryable(PlaywrightResultExecutor.class.getMethod("check", Page.class, PurchaseReceipt.class));
    }

    @Test
    @DisplayName("구매(buy)는 멱등하지 않으므로 재시도를 선언하지 않는다")
    void shouldNotDeclareRetryOnPurchase() throws NoSuchMethodException {
        // 준비: 구매 메서드를 조회한다
        Method buy = PlaywrightPurchaseExecutor.class.getMethod("buy", Page.class, LottoUser.class, BooleanSupplier.class);

        // 실행 및 검증: 중복 구매 방지를 위해 Retryable을 선언하지 않는다
        then(buy.getAnnotation(Retryable.class)).isNull();
    }

    private void assertRetryable(Method method) {
        Retryable retryable = method.getAnnotation(Retryable.class);

        then(retryable).isNotNull();
        then(retryable.includes()).containsExactly(PlaywrightException.class);
        then(retryable.maxRetriesString()).isEqualTo("${lotto.max-retries:3}");
        then(retryable.delayString()).isEqualTo("${lotto.retry-delay-ms:2000}");
    }
}
