package org.nowstart.lotto.adapter.out.browser;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.PurchaseReceipt;
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
@DisplayName("Playwright 구매 재시도")
class PlaywrightPurchaseExecutorRetryTest {

    @Autowired
    private PlaywrightPurchaseExecutor playwrightPurchaseExecutor;

    @Test
    @DisplayName("자동 번호 선택이 일시 실패하면 설정된 횟수만큼 구매를 재시도한다")
    void shouldRetryBuyWhenAutoNumberSelectionFailsTransiently() {
        // 준비: 자동 번호 선택이 두 번 실패한 뒤 성공하는 구매 페이지가 있다
        Page page = mock(Page.class);
        Locator autoNumber = mock(Locator.class);
        Locator quantityBox = mock(Locator.class);
        Locator confirmButton = mock(Locator.class);
        Locator purchaseButton = mock(Locator.class);
        Locator finalConfirmButton = mock(Locator.class);
        LottoUser user = new LottoUser("user1", "password", 2, "user1@nowstart.org", false);

        given(page.locator(LottoBrowserConstants.AUTO_NUMBER)).willReturn(autoNumber);
        given(page.locator(LottoBrowserConstants.QUANTITY_BOX)).willReturn(quantityBox);
        given(page.locator(LottoBrowserConstants.CONFIRM_BTN)).willReturn(confirmButton);
        given(page.locator(LottoBrowserConstants.PURCHASE_BTN)).willReturn(purchaseButton);
        given(page.locator(LottoBrowserConstants.FINAL_CONFIRM_BTN)).willReturn(finalConfirmButton);

        AtomicInteger attempts = new AtomicInteger();
        willAnswer(invocation -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("temporary");
            }
            return null;
        }).given(autoNumber).click();

        // 실행: 구매를 실행한다
        PurchaseReceipt purchaseReceipt = playwrightPurchaseExecutor.buy(page, user);

        // 검증: 세 번째 시도에서 구매 영수증이 반환되고 각 구매 단계가 실행된다
        then(purchaseReceipt.count()).isEqualTo(2);
        then(purchaseReceipt.purchaseDate()).isNotNull();
        then(attempts.get()).isEqualTo(3);
        BDDMockito.then(page).should(times(3)).navigate(LottoBrowserConstants.URL_PURCHASE);
        BDDMockito.then(page).should(times(3)).waitForLoadState(LoadState.NETWORKIDLE);
        BDDMockito.then(quantityBox).should().selectOption("2");
        BDDMockito.then(confirmButton).should().click();
        BDDMockito.then(purchaseButton).should().click();
        BDDMockito.then(finalConfirmButton).should().click();
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
