package org.nowstart.lotto.adapter.out.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.function.BooleanSupplier;
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
     * 최종 확정(실결제) 클릭 직전에 abortRequested를 확인해, 호출자 타임아웃으로 중단 신호가 온 경우
     * 확정하지 않고 Optional.empty()를 반환한다(중복/뒤늦은 실구매 방지).
     */
    public Optional<PurchaseReceipt> buy(Page page, LottoUser user, BooleanSupplier abortRequested) {
        log.info("[Purchase][{}] Start count={}", user.id(), user.count());

        page.navigate(LottoBrowserConstants.URL_PURCHASE);
        page.waitForLoadState(LoadState.NETWORKIDLE);

        page.locator(LottoBrowserConstants.AUTO_NUMBER).click();
        page.locator(LottoBrowserConstants.QUANTITY_BOX).selectOption(String.valueOf(user.count()));
        page.locator(LottoBrowserConstants.CONFIRM_BTN).click();
        page.locator(LottoBrowserConstants.PURCHASE_BTN).click();

        Locator finalConfirmButton = page.locator(LottoBrowserConstants.FINAL_CONFIRM_BTN);

        // 실결제(최종 확정) 직전 마지막 취소 확인 — 여기까지 진행된 뒤 타임아웃된 작업이 실구매하는 것을 방지.
        if (abortRequested.getAsBoolean()) {
            log.warn("[Purchase][{}] Aborted before final confirmation (호출자 타임아웃) - 구매 미수행", user.id());
            return Optional.empty();
        }

        finalConfirmButton.click();
        waitForFinalConfirmDialogToClose(finalConfirmButton, user);

        log.info("[Purchase][{}] Success", user.id());
        return Optional.of(new PurchaseReceipt(user.count(), LocalDate.now(LOTTO_ZONE)));
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
