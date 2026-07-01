package org.nowstart.lotto.adapter.out.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
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
    private static final double FINAL_CONFIRM_VISIBLE_TIMEOUT_MS = 30_000;
    private static final double FINAL_CONFIRM_CLICK_TIMEOUT_MS = 3_000;

    /**
     * 주의: 구매는 멱등하지 않으므로 절대 @Retryable을 적용하지 않는다.
     * - 최종 확정 클릭 '직전'까지만 abort를 확인한다(확정 전 중단 시 Optional.empty()).
     * - 일단 클릭을 시도한 뒤에는(사이트에 요청이 전송됐을 수 있으므로) 클릭이 타임아웃/예외로 끝나더라도
     *   실패로 단정하지 않고 영수증을 반환한다 → 호출자가 구매 후 원장 확인으로 성사 여부를 판정한다.
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

        // 2) 대기 직후, 실결제 클릭 직전에 마지막으로 abort를 확인한다(여기까진 아직 미클릭).
        if (abortRequested.getAsBoolean()) {
            log.warn("[Purchase][{}] Aborted before final confirmation (호출자 타임아웃) - 구매 미수행", user.id());
            return Optional.empty();
        }

        // 3) 여기서부터는 클릭이 실제 전송될 수 있다. 짧은 타임아웃으로 즉시 클릭하되,
        //    클릭이 예외로 끝나도(응답 지연 등) 실패로 단정하지 않고 원장 확인으로 넘긴다.
        try {
            finalConfirmButton.click(new Locator.ClickOptions().setTimeout(FINAL_CONFIRM_CLICK_TIMEOUT_MS));
            waitForFinalConfirmDialogToClose(finalConfirmButton, user);
        } catch (PlaywrightException clickException) {
            log.warn("[Purchase][{}] Final confirm click did not complete cleanly (타임아웃/오류) - "
                    + "구매 성사 여부는 원장 확인으로 검증", user.id(), clickException);
        }

        log.info("[Purchase][{}] Confirm click attempted - proceeding to ledger verification", user.id());
        return Optional.of(new PurchaseReceipt(user.count(), LocalDate.now(LOTTO_ZONE)));
    }

    private void waitForFinalConfirmDialogToClose(Locator finalConfirmButton, LottoUser user) {
        finalConfirmButton.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.HIDDEN)
                .setTimeout(10_000));
    }
}
