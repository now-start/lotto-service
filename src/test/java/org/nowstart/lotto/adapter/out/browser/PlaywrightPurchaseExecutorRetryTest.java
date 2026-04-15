package org.nowstart.lotto.adapter.out.browser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.nowstart.lotto.domain.model.LottoUser;
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
class PlaywrightPurchaseExecutorRetryTest {

    @Autowired
    private PlaywrightPurchaseExecutor playwrightPurchaseExecutor;

    @Test
    void shouldRetryBuyWhenAutoNumberSelectionFailsTransiently() {
        Page page = mock(Page.class);
        Locator autoNumber = mock(Locator.class);
        Locator quantityBox = mock(Locator.class);
        Locator confirmButton = mock(Locator.class);
        Locator purchaseButton = mock(Locator.class);
        Locator finalConfirmButton = mock(Locator.class);
        LottoUser user = new LottoUser("user1", "password", 2, "user1@nowstart.org", false);

        when(page.locator(LottoBrowserConstants.AUTO_NUMBER)).thenReturn(autoNumber);
        when(page.locator(LottoBrowserConstants.QUANTITY_BOX)).thenReturn(quantityBox);
        when(page.locator(LottoBrowserConstants.CONFIRM_BTN)).thenReturn(confirmButton);
        when(page.locator(LottoBrowserConstants.PURCHASE_BTN)).thenReturn(purchaseButton);
        when(page.locator(LottoBrowserConstants.FINAL_CONFIRM_BTN)).thenReturn(finalConfirmButton);

        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("temporary");
            }
            return null;
        }).when(autoNumber).click();

        playwrightPurchaseExecutor.buy(page, user);

        assertThat(attempts.get()).isEqualTo(3);
        verify(page, times(3)).navigate(LottoBrowserConstants.URL_PURCHASE);
        verify(page, times(3)).waitForLoadState(LoadState.NETWORKIDLE);
        verify(quantityBox).selectOption("2");
        verify(confirmButton).click();
        verify(purchaseButton).click();
        verify(finalConfirmButton).click();
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
