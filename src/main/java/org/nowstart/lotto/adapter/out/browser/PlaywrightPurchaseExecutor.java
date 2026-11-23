package org.nowstart.lotto.adapter.out.browser;

import com.microsoft.playwright.Dialog;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.PurchaseSubmission;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.PurchaseSubmission.Status;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort.LottoUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PlaywrightPurchaseExecutor {

    private static final double FINAL_CONFIRM_VISIBLE_TIMEOUT_MS = 30_000;
    private static final double FINAL_CONFIRM_CLICK_TIMEOUT_MS = 3_000;
    private static final double FINAL_CONFIRM_HIDDEN_TIMEOUT_MS = 10_000;

    private final boolean dryRun;

    public PlaywrightPurchaseExecutor(@Value("${lotto.dry-run:false}") boolean dryRun) {
        this.dryRun = dryRun;
    }

    /**
     * 주의: 구매는 멱등하지 않으므로 절대 재시도하지 않는다.
     * <ul>
     *   <li>최종 확정 클릭 직전까지만 중단 신호와 dry-run을 확인한다. 여기서 멈추면 구매 미수행이 확실하다.</li>
     *   <li>확정 클릭을 시도한 뒤에는, 클릭이 예외로 끝나도 {@link Status#CLICKED}를 돌려준다.
     *       과부하 사이트에서는 예외가 나도 요청이 이미 전송됐을 수 있으므로, 구매 여부는 호출자가 원장·예치금으로 판정한다.</li>
     *   <li>브라우저 대화상자 문구는 수집해 사이트의 거부 사유 판단에 쓰게 한다(대화상자는 기존처럼 닫는다).</li>
     * </ul>
     */
    public PurchaseSubmission buy(Page page, LottoUser user, int count, BooleanSupplier abortRequested) {
        log.info("[Purchase][{}] Start count={}", user.id(), count);
        List<String> dialogMessages = new CopyOnWriteArrayList<>();
        Consumer<Dialog> dialogHandler = dialog -> {
            dialogMessages.add(dialog.message());
            log.warn("[Purchase][{}] Dialog type={} message={}", user.id(), dialog.type(), dialog.message());
            try {
                dialog.dismiss();
            } catch (PlaywrightException exception) {
                log.warn("[Purchase][{}] Dialog dismiss failed", user.id(), exception);
            }
        };
        page.onDialog(dialogHandler);
        try {
            Locator finalConfirmButton = openFinalConfirmation(page, user, count, dialogMessages);

            if (abortRequested.getAsBoolean()) {
                log.warn("[Purchase][{}] Aborted before final confirmation - 구매 미수행", user.id());
                return new PurchaseSubmission(Status.NOT_CLICKED, dialogMessages);
            }
            if (dryRun) {
                log.warn("[Purchase][{}] DRY-RUN - 최종 확정 직전에 중단 count={}", user.id(), count);
                return new PurchaseSubmission(Status.DRY_RUN, dialogMessages);
            }

            // 여기서부터는 클릭이 전송됐을 수 있으므로 어떤 예외가 나도 CLICKED를 돌려준다.
            int dialogsBeforeClick = dialogMessages.size();
            try {
                finalConfirmButton.click(new Locator.ClickOptions().setTimeout(FINAL_CONFIRM_CLICK_TIMEOUT_MS));
            } catch (RuntimeException clickException) {
                log.warn("[Purchase][{}] Final confirm click threw - 구매 여부는 원장으로 판정", user.id(), clickException);
            }
            waitForConfirmDialogToClose(finalConfirmButton);
            log.info("[Purchase][{}] Final confirmation attempted - proceeding to ledger verification", user.id());
            // 거부 판단에는 클릭 이후에 뜬 대화상자만 쓴다(클릭 전 안내 문구를 거부로 오인하지 않도록).
            List<String> afterClick = List.copyOf(dialogMessages.subList(dialogsBeforeClick, dialogMessages.size()));
            return new PurchaseSubmission(Status.CLICKED, afterClick);
        } finally {
            try {
                page.offDialog(dialogHandler);
            } catch (RuntimeException exception) {
                log.warn("[Purchase][{}] Dialog handler removal failed (page closed?)", user.id(), exception);
            }
        }
    }

    private Locator openFinalConfirmation(Page page, LottoUser user, int count, List<String> dialogMessages) {
        try {
            page.navigate(LottoBrowserConstants.URL_PURCHASE);
            page.waitForLoadState(LoadState.NETWORKIDLE);

            page.locator(LottoBrowserConstants.AUTO_NUMBER).click();
            page.locator(LottoBrowserConstants.QUANTITY_BOX).selectOption(String.valueOf(count));
            page.locator(LottoBrowserConstants.CONFIRM_BTN).click();
            page.locator(LottoBrowserConstants.PURCHASE_BTN).click();

            Locator finalConfirmButton = page.locator(LottoBrowserConstants.FINAL_CONFIRM_BTN);
            finalConfirmButton.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(FINAL_CONFIRM_VISIBLE_TIMEOUT_MS));
            return finalConfirmButton;
        } catch (RuntimeException exception) {
            if (dialogMessages.isEmpty()) {
                throw exception;
            }
            // 사이트가 대화상자로 사유를 알려 준 경우(판매 시간 아님 등) 그 문구를 오류에 남긴다.
            throw new IllegalStateException("구매 화면 진행 실패 (사이트 메시지: " + String.join(" / ", dialogMessages)
                    + ") user=" + user.id(), exception);
        }
    }

    private void waitForConfirmDialogToClose(Locator finalConfirmButton) {
        try {
            finalConfirmButton.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN)
                    .setTimeout(FINAL_CONFIRM_HIDDEN_TIMEOUT_MS));
        } catch (RuntimeException exception) {
            log.warn("[Purchase] Confirm dialog still visible after click - 원장으로 판정");
        }
    }
}
