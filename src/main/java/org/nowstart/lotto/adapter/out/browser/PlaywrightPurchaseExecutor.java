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
    private static final double FINAL_CONFIRM_HIDDEN_TIMEOUT_MS = 10_000;

    /**
     * 주의: 구매는 멱등하지 않으므로 절대 @Retryable을 적용하지 않는다.
     * - 최종 확정 클릭 '직전'까지만 abort를 확인한다(확정 전 중단 시 Optional.empty()).
     * - 클릭이 실제 '제출'됐는지는 확정 다이얼로그가 닫혔는지로 판정한다(이 사이트는 확정 성공 시 다이얼로그가 닫힘).
     *   · 닫힘 = 제출됨 → 영수증 반환(호출자가 구매 후 원장 확인으로 최종 검증). 클릭이 응답 지연 등으로 예외를
     *     던졌더라도 다이얼로그가 닫혔다면 제출된 것으로 본다.
     *   · 여전히 열림 = actionability 실패 등으로 미제출 → 영수증을 만들지 않는다(안 산 구매가 원장의 옛 행에
     *     오매칭되어 성공 통지되는 것을 방지).
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

        // 1) 확정 버튼이 뜰 때까지 먼저 대기(아직 클릭하지 않음).
        finalConfirmButton.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(FINAL_CONFIRM_VISIBLE_TIMEOUT_MS));

        // 2) 실결제 클릭 직전에 마지막으로 abort 확인(여기까진 미클릭 확실).
        if (abortRequested.getAsBoolean()) {
            log.warn("[Purchase][{}] Aborted before final confirmation (호출자 타임아웃) - 구매 미수행", user.id());
            return Optional.empty();
        }

        // 3) 짧은 타임아웃으로 클릭 시도. 예외가 나도 실제 제출 여부는 다이얼로그 상태로 판정한다.
        try {
            finalConfirmButton.click(new Locator.ClickOptions().setTimeout(FINAL_CONFIRM_CLICK_TIMEOUT_MS));
        } catch (PlaywrightException clickException) {
            log.warn("[Purchase][{}] Final confirm click threw (actionability/응답 지연 등) - "
                    + "다이얼로그 상태로 제출 여부 판정", user.id(), clickException);
        }

        // 4) 확정 다이얼로그가 닫혔으면 제출된 것 → 영수증(원장 검증). 여전히 열려 있으면 미제출 → 영수증 없음.
        if (!confirmDialogClosed(finalConfirmButton)) {
            log.warn("[Purchase][{}] Final confirm dialog still open - 확정 미제출로 판단, 구매 미수행", user.id());
            return Optional.empty();
        }

        log.info("[Purchase][{}] Confirmation submitted (dialog closed) - proceeding to ledger verification", user.id());
        return Optional.of(new PurchaseReceipt(user.count(), LocalDate.now(LOTTO_ZONE)));
    }

    private boolean confirmDialogClosed(Locator finalConfirmButton) {
        try {
            finalConfirmButton.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN)
                    .setTimeout(FINAL_CONFIRM_HIDDEN_TIMEOUT_MS));
            return true;
        } catch (PlaywrightException exception) {
            return false;
        }
    }
}
