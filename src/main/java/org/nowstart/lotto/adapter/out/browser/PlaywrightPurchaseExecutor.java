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
    // 확정 버튼이 뜰 때까지의 대기(이 구간에서 타임아웃 나도 아직 클릭하지 않는다).
    private static final double FINAL_CONFIRM_VISIBLE_TIMEOUT_MS = 30_000;
    // 이미 actionable해진 버튼을 즉시 누르기 위한 짧은 클릭 타임아웃(긴 actionability 대기 창 제거).
    private static final double FINAL_CONFIRM_CLICK_TIMEOUT_MS = 3_000;

    /**
     * 주의: 구매는 멱등하지 않으므로 절대 @Retryable을 적용하지 않는다.
     * 최종 확정(실결제) 클릭 직전에 abortRequested를 확인해, 호출자 타임아웃으로 중단 신호가 온 경우
     * 확정하지 않고 Optional.empty()를 반환한다. click() 자체의 actionability 대기 중 결제되는 것을 막기 위해
     * "버튼 대기 → abort 재확인 → 짧은 타임아웃으로 즉시 클릭" 순서로 클릭 창을 최소화한다.
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

        // 1) 확정 버튼이 클릭 가능해질 때까지 먼저 대기한다(아직 클릭하지 않음).
        finalConfirmButton.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(FINAL_CONFIRM_VISIBLE_TIMEOUT_MS));

        // 2) 대기 직후, 실결제 클릭 직전에 마지막으로 abort를 확인한다.
        if (abortRequested.getAsBoolean()) {
            log.warn("[Purchase][{}] Aborted before final confirmation (호출자 타임아웃) - 구매 미수행", user.id());
            return Optional.empty();
        }

        // 3) 이미 actionable하므로 짧은 타임아웃으로 즉시 클릭한다(긴 대기 창을 없애 타임아웃 후 실결제 방지).
        finalConfirmButton.click(new Locator.ClickOptions().setTimeout(FINAL_CONFIRM_CLICK_TIMEOUT_MS));
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
