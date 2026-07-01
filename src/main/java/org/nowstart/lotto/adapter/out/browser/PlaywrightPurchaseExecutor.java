package org.nowstart.lotto.adapter.out.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.PurchaseReceipt;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort.LottoUser;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PlaywrightPurchaseExecutor {

    private static final ZoneId LOTTO_ZONE = ZoneId.of("Asia/Seoul");

    /**
     * 주의: 구매는 멱등하지 않으므로 절대 @Retryable을 적용하지 않는다.
     * 최종 확정 클릭 이후에 예외가 발생하면 재시도가 중복 구매(실거래)를 유발할 수 있다.
     * 일시적 오류는 구매 후 원장 확인(PlaywrightResultExecutor.check)에서 검증한다.
     */
    public PurchaseReceipt buy(Page page, LottoUser user) {
        log.info("[Purchase][{}] Start count={}", user.id(), user.count());

        page.navigate(LottoBrowserConstants.URL_PURCHASE);
        page.waitForLoadState(LoadState.NETWORKIDLE);

        page.locator(LottoBrowserConstants.AUTO_NUMBER).click();
        page.locator(LottoBrowserConstants.QUANTITY_BOX).selectOption(String.valueOf(user.count()));
        page.locator(LottoBrowserConstants.CONFIRM_BTN).click();
        page.locator(LottoBrowserConstants.PURCHASE_BTN).click();
        Locator finalConfirmButton = page.locator(LottoBrowserConstants.FINAL_CONFIRM_BTN);
        finalConfirmButton.click();
        waitForFinalConfirmDialogToClose(finalConfirmButton, user);

        log.info("[Purchase][{}] Success", user.id());
        return new PurchaseReceipt(user.count(), LocalDate.now(LOTTO_ZONE));
    }

    private void waitForFinalConfirmDialogToClose(Locator finalConfirmButton, LottoUser user) {
        try {
            finalConfirmButton.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN)
                    .setTimeout(10_000));
        } catch (Exception exception) {
            log.warn("[Purchase][{}] Final confirm dialog did not close within timeout; "
                    + "purchase ledger check will verify the result", user.id(), exception);
        }
    }
}
