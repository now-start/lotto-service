package org.nowstart.lotto.adapter.outbound.browser

import com.microsoft.playwright.Dialog
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.PlaywrightException
import com.microsoft.playwright.options.LoadState
import com.microsoft.playwright.options.WaitForSelectorState
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer
import org.nowstart.lotto.application.port.outbound.UserAccount
import org.nowstart.lotto.application.port.outbound.PurchaseSubmission
import org.nowstart.lotto.application.port.outbound.PurchaseSubmission.Status
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

private val log = LoggerFactory.getLogger(PlaywrightPurchaseExecutor::class.java)

@Component
class PlaywrightPurchaseExecutor(private val settings: BrowserSettings) {

    /**
     * 주의: 구매는 멱등하지 않으므로 절대 재시도하지 않는다.
     * - 최종 확정 클릭 직전까지만 중단 신호와 dry-run을 확인한다. 여기서 멈추면 구매 미수행이 확실하다.
     * - 확정 클릭을 시도한 뒤에는, 클릭이 예외로 끝나도 [Status.CLICKED]를 돌려준다.
     *   과부하 사이트에서는 예외가 나도 요청이 이미 전송됐을 수 있으므로, 구매 여부는 호출자가 원장·예치금으로 판정한다.
     * - 최종 클릭 이후 사이트가 구매를 거부한 문구만 호출자에게 돌려준다.
     */
    fun buy(page: Page, user: UserAccount, count: Int, abortRequested: () -> Boolean): PurchaseSubmission {
        log.info("[Purchase][{}] Start count={}", user.id, count)
        val dialogMessages = CopyOnWriteArrayList<String>()
        val dialogHandler = Consumer<Dialog> { dialog ->
            dialogMessages += dialog.message()
            log.warn("[Purchase][{}] Dialog type={} message={}", user.id, dialog.type(), dialog.message())
            try {
                dialog.dismiss()
            } catch (exception: PlaywrightException) {
                log.warn("[Purchase][{}] Dialog dismiss failed", user.id, exception)
            }
        }
        page.onDialog(dialogHandler)
        try {
            val finalConfirmButton = openFinalConfirmation(page, user, count, dialogMessages)

            if (abortRequested()) {
                log.warn("[Purchase][{}] Aborted before final confirmation - 구매 미수행", user.id)
                return PurchaseSubmission(Status.NOT_CLICKED)
            }
            if (settings.dryRun) {
                log.warn("[Purchase][{}] DRY-RUN - 최종 확정 직전에 중단 count={}", user.id, count)
                return PurchaseSubmission(Status.DRY_RUN)
            }

            // 여기서부터는 클릭이 전송됐을 수 있으므로 어떤 예외가 나도 CLICKED를 돌려준다.
            val dialogsBeforeClick = dialogMessages.size
            try {
                finalConfirmButton.click(Locator.ClickOptions().setTimeout(FINAL_CONFIRM_CLICK_TIMEOUT_MS))
            } catch (clickException: RuntimeException) {
                log.warn("[Purchase][{}] Final confirm click threw - 구매 여부는 원장으로 판정", user.id, clickException)
            }
            waitForConfirmDialogToClose(finalConfirmButton)
            log.info("[Purchase][{}] Final confirmation attempted - proceeding to ledger verification", user.id)
            val rejections = dialogMessages.drop(dialogsBeforeClick).filter { message ->
                REJECTION_HINTS.any { hint -> hint in message }
            }
            return PurchaseSubmission(Status.CLICKED, rejections)
        } finally {
            try {
                page.offDialog(dialogHandler)
            } catch (exception: RuntimeException) {
                log.warn("[Purchase][{}] Dialog handler removal failed (page closed?)", user.id, exception)
            }
        }
    }

    private fun openFinalConfirmation(page: Page, user: UserAccount, count: Int, dialogMessages: List<String>): Locator {
        try {
            page.navigate(BrowserConstants.URL_PURCHASE)
            page.waitForLoadState(LoadState.NETWORKIDLE)

            page.locator(BrowserConstants.AUTO_NUMBER).click()
            page.locator(BrowserConstants.QUANTITY_BOX).selectOption(count.toString())
            page.locator(BrowserConstants.CONFIRM_BTN).click()
            page.locator(BrowserConstants.PURCHASE_BTN).click()

            val finalConfirmButton = page.locator(BrowserConstants.FINAL_CONFIRM_BTN)
            finalConfirmButton.waitFor(
                Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(FINAL_CONFIRM_VISIBLE_TIMEOUT_MS),
            )
            return finalConfirmButton
        } catch (exception: RuntimeException) {
            if (dialogMessages.isEmpty()) {
                throw exception
            }
            // 사이트가 대화상자로 사유를 알려 준 경우(판매 시간 아님 등) 그 문구를 오류에 남긴다.
            throw IllegalStateException(
                "구매 화면 진행 실패 (사이트 메시지: ${dialogMessages.joinToString(" / ")}) user=${user.id}",
                exception,
            )
        }
    }

    private fun waitForConfirmDialogToClose(finalConfirmButton: Locator) {
        try {
            finalConfirmButton.waitFor(
                Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN).setTimeout(FINAL_CONFIRM_HIDDEN_TIMEOUT_MS),
            )
        } catch (exception: RuntimeException) {
            log.warn("[Purchase] Confirm dialog still visible after click - 원장으로 판정")
        }
    }

    private companion object {
        private val REJECTION_HINTS = listOf("한도", "판매시간", "판매 시간", "구매할 수 없", "구매가 불가", "잔액이 부족", "예치금이 부족")
        const val FINAL_CONFIRM_VISIBLE_TIMEOUT_MS = 30_000.0
        const val FINAL_CONFIRM_CLICK_TIMEOUT_MS = 3_000.0
        const val FINAL_CONFIRM_HIDDEN_TIMEOUT_MS = 10_000.0
    }
}
