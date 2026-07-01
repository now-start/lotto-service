package org.nowstart.lotto.adapter.out.browser;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort.LottoUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = PlaywrightPurchaseExecutorRetryTest.Config.class)
@TestPropertySource(properties = {
        "lotto.max-retries=3",
        "lotto.retry-delay-ms=1"
})
@DisplayName("Playwright 구매 재시도 정책")
class PlaywrightPurchaseExecutorRetryTest {

    @Autowired
    private PlaywrightPurchaseExecutor playwrightPurchaseExecutor;

    @Test
    @DisplayName("구매는 실패해도 재시도하지 않고 예외를 전파한다 (멱등하지 않은 실거래 보호)")
    void shouldNotRetryBuyOnFailure() {
        // 준비: 자동 번호 선택 단계에서 실패하는 구매 페이지가 있다
        Page page = mock(Page.class);
        Locator autoNumber = mock(Locator.class);
        LottoUser user = new LottoUser("user1", "password", 2, "user1@nowstart.org", false);
        given(page.locator(LottoBrowserConstants.AUTO_NUMBER)).willReturn(autoNumber);

        AtomicInteger attempts = new AtomicInteger();
        willAnswer(invocation -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("temporary");
        }).given(autoNumber).click();

        // 실행 및 검증: 예외가 그대로 전파되고 단 한 번만 시도한다 (재시도 없음)
        thenThrownBy(() -> playwrightPurchaseExecutor.buy(page, user))
                .isInstanceOf(IllegalStateException.class);
        then(attempts.get()).isEqualTo(1);
        BDDMockito.then(page).should(times(1)).navigate(LottoBrowserConstants.URL_PURCHASE);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableResilientMethods
    static class Config {

        @Bean
        PlaywrightPurchaseExecutor playwrightPurchaseExecutor() {
            return new PlaywrightPurchaseExecutor();
        }
    }
}
