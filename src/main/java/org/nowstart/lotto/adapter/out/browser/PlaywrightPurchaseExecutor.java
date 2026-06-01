package org.nowstart.lotto.adapter.out.browser;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.PurchaseReceipt;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort.LottoUser;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PlaywrightPurchaseExecutor {

    private static final ZoneId LOTTO_ZONE = ZoneId.of("Asia/Seoul");

    @Retryable(
            includes = Exception.class,
            maxRetriesString = "${lotto.max-retries:3}",
            delayString = "${lotto.retry-delay-ms:2000}"
    )
    public PurchaseReceipt buy(Page page, LottoUser user) {
        log.info("[Purchase][{}] Start count={}", user.id(), user.count());

        page.navigate(LottoBrowserConstants.URL_PURCHASE);
        page.waitForLoadState(LoadState.NETWORKIDLE);

        page.locator(LottoBrowserConstants.AUTO_NUMBER).click();
        page.locator(LottoBrowserConstants.QUANTITY_BOX).selectOption(String.valueOf(user.count()));
        page.locator(LottoBrowserConstants.CONFIRM_BTN).click();
        page.locator(LottoBrowserConstants.PURCHASE_BTN).click();
        var finalConfirmButton = page.locator(LottoBrowserConstants.FINAL_CONFIRM_BTN);
        finalConfirmButton.click();
        waitForFinalConfirmDialogToClose(finalConfirmButton, user);

        log.info("[Purchase][{}] Success", user.id());
        return new PurchaseReceipt(user.count(), LocalDate.now(LOTTO_ZONE));
    }

    private void waitForFinalConfirmDialogToClose(com.microsoft.playwright.Locator finalConfirmButton, LottoUser user) {
        try {
            finalConfirmButton.waitFor(new com.microsoft.playwright.Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN)
                    .setTimeout(10_000));
        } catch (Exception exception) {
            log.warn("[Purchase][{}] Final confirm dialog did not close within timeout; "
                    + "purchase ledger check will verify the result", user.id(), exception);
        }
    }
}
