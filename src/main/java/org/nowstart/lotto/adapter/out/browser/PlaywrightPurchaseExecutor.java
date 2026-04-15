package org.nowstart.lotto.adapter.out.browser;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.domain.model.LottoUser;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PlaywrightPurchaseExecutor {

    @Retryable(
            includes = Exception.class,
            maxRetriesString = "${lotto.max-retries:3}",
            delayString = "${lotto.retry-delay-ms:2000}"
    )
    public void buy(Page page, LottoUser user) {
        log.info("[Purchase][{}] Start count={}", user.id(), user.count());

        page.navigate(LottoBrowserConstants.URL_PURCHASE);
        page.waitForLoadState(LoadState.NETWORKIDLE);

        page.locator(LottoBrowserConstants.AUTO_NUMBER).click();
        page.locator(LottoBrowserConstants.QUANTITY_BOX).selectOption(String.valueOf(user.count()));
        page.locator(LottoBrowserConstants.CONFIRM_BTN).click();
        page.locator(LottoBrowserConstants.PURCHASE_BTN).click();
        page.locator(LottoBrowserConstants.FINAL_CONFIRM_BTN).click();

        log.info("[Purchase][{}] Success", user.id());
    }
}
