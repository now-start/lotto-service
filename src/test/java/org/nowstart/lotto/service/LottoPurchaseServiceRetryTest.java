package org.nowstart.lotto.service;

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
import org.nowstart.lotto.data.properties.LottoProperties;
import org.nowstart.lotto.data.type.LottoConstantsType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = LottoPurchaseServiceRetryTest.Config.class)
@TestPropertySource(properties = {
        "lotto.max-retries=3",
        "lotto.retry-delay-ms=1"
})
class LottoPurchaseServiceRetryTest {

    @Autowired
    private LottoPurchaseService lottoPurchaseService;

    @Test
    void shouldRetryBuyWhenAutoNumberSelectionFailsTransiently() {
        Page page = mock(Page.class);
        Locator autoNumber = mock(Locator.class);
        Locator quantityBox = mock(Locator.class);
        Locator confirmButton = mock(Locator.class);
        Locator purchaseButton = mock(Locator.class);
        Locator finalConfirmButton = mock(Locator.class);
        LottoProperties.User user = new LottoProperties.User();
        user.setId("user1");
        user.setCount(2);

        when(page.locator(LottoConstantsType.AUTO_NUMBER.getValue())).thenReturn(autoNumber);
        when(page.locator(LottoConstantsType.QUANTITY_BOX.getValue())).thenReturn(quantityBox);
        when(page.locator(LottoConstantsType.CONFIRM_BTN.getValue())).thenReturn(confirmButton);
        when(page.locator(LottoConstantsType.PURCHASE_BTN.getValue())).thenReturn(purchaseButton);
        when(page.locator(LottoConstantsType.FINAL_CONFIRM_BTN.getValue())).thenReturn(finalConfirmButton);

        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("temporary");
            }
            return null;
        }).when(autoNumber).click();

        lottoPurchaseService.buy(page, user);

        assertThat(attempts.get()).isEqualTo(3);
        verify(page, times(3)).navigate(LottoConstantsType.URL_PURCHASE.getValue());
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
        LottoPurchaseService lottoPurchaseService() {
            return new LottoPurchaseService();
        }
    }
}
