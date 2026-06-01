package org.nowstart.lotto.adapter.out.browser;

import static org.assertj.core.api.BDDAssertions.then;

import com.microsoft.playwright.Page;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.PurchaseReceipt;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort.LottoUser;
import org.springframework.resilience.annotation.Retryable;

@DisplayName("Playwright 재시도 애너테이션")
class PlaywrightRetryAnnotationTest {

    @Test
    @DisplayName("브라우저 외부 호출 executor는 Retryable 설정을 선언한다")
    void shouldDeclareRetryOnTransientBrowserExecutors() throws NoSuchMethodException {
        // 준비: 브라우저를 호출하는 executor 메서드들이 있다
        // 실행 및 검증: 각 메서드가 동일한 재시도 정책을 가진다
        assertRetryable(PlaywrightLoginExecutor.class.getMethod("login", Page.class, LottoUser.class));
        assertRetryable(PlaywrightPurchaseExecutor.class.getMethod("buy", Page.class, LottoUser.class));
        assertRetryable(PlaywrightResultExecutor.class.getMethod("check", Page.class));
        assertRetryable(PlaywrightResultExecutor.class.getMethod(
                "check",
                Page.class,
                PurchaseReceipt.class
        ));
    }

    private void assertRetryable(Method method) {
        Retryable retryable = method.getAnnotation(Retryable.class);

        then(retryable).isNotNull();
        then(retryable.includes()).containsExactly(Exception.class);
        then(retryable.maxRetriesString()).isEqualTo("${lotto.max-retries:3}");
        then(retryable.delayString()).isEqualTo("${lotto.retry-delay-ms:2000}");
    }
}
