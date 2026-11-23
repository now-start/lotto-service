package org.nowstart.lotto.adapter.out.browser;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.microsoft.playwright.Dialog;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort.LottoUser;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.PurchaseSubmission;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.PurchaseSubmission.Status;

@DisplayName("Playwright 구매 신청")
class PlaywrightPurchaseExecutorTest {

    private final LottoUser user = new LottoUser("user1", "password", 5, "user1@nowstart.org", false);
    private final Page page = mock(Page.class);
    private final Locator purchaseButton = mock(Locator.class);
    private final Locator finalConfirm = mock(Locator.class);

    @BeforeEach
    void setUp() {
        given(page.locator(LottoBrowserConstants.AUTO_NUMBER)).willReturn(mock(Locator.class));
        given(page.locator(LottoBrowserConstants.QUANTITY_BOX)).willReturn(mock(Locator.class));
        given(page.locator(LottoBrowserConstants.CONFIRM_BTN)).willReturn(mock(Locator.class));
        given(page.locator(LottoBrowserConstants.PURCHASE_BTN)).willReturn(purchaseButton);
        given(page.locator(LottoBrowserConstants.FINAL_CONFIRM_BTN)).willReturn(finalConfirm);
    }

    @Test
    @DisplayName("dry-run이면 최종 확정 직전에 멈추고 클릭하지 않는다")
    void shouldStopBeforeFinalClickInDryRun() {
        PurchaseSubmission submission = new PlaywrightPurchaseExecutor(true).buy(page, user, 3, () -> false);

        then(submission.status()).isEqualTo(Status.DRY_RUN);
        BDDMockito.then(finalConfirm).should(never()).click(any(Locator.ClickOptions.class));
    }

    @Test
    @DisplayName("중단 요청이 있으면 확정 클릭 전에 멈춘다(구매 미수행 확실)")
    void shouldNotClickWhenAborted() {
        PurchaseSubmission submission = new PlaywrightPurchaseExecutor(false).buy(page, user, 3, () -> true);

        then(submission.status()).isEqualTo(Status.NOT_CLICKED);
        BDDMockito.then(finalConfirm).should(never()).click(any(Locator.ClickOptions.class));
    }

    @Test
    @DisplayName("확정 클릭이 예외로 끝나도 '클릭함'으로 돌려준다 — 요청이 이미 전송됐을 수 있어 원장으로 판정해야 한다")
    void shouldReportClickedEvenWhenClickThrows() {
        willThrow(new TimeoutError("click timeout")).given(finalConfirm).click(any(Locator.ClickOptions.class));
        willThrow(new TimeoutError("still visible")).given(finalConfirm).waitFor(org.mockito.ArgumentMatchers.argThat(
                options -> options != null && options.state == com.microsoft.playwright.options.WaitForSelectorState.HIDDEN));

        PurchaseSubmission submission = new PlaywrightPurchaseExecutor(false).buy(page, user, 3, () -> false);

        then(submission.status()).isEqualTo(Status.CLICKED);
    }

    @Test
    @DisplayName("구매 화면에서 뜬 대화상자 문구를 모아 돌려준다")
    void shouldCollectDialogMessages() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<Dialog>> handler = ArgumentCaptor.forClass(Consumer.class);
        willAnswer(invocation -> null).given(page).onDialog(handler.capture());
        Dialog dialog = mock(Dialog.class);
        given(dialog.message()).willReturn("이번 회차 구매한도를 초과했습니다");
        given(dialog.type()).willReturn("alert");
        willAnswer(invocation -> {
            handler.getValue().accept(dialog);
            return null;
        }).given(finalConfirm).click(any(Locator.ClickOptions.class));

        PurchaseSubmission submission = new PlaywrightPurchaseExecutor(false).buy(page, user, 3, () -> false);

        then(submission.status()).isEqualTo(Status.CLICKED);
        then(submission.dialogMessages()).containsExactly("이번 회차 구매한도를 초과했습니다");
        BDDMockito.then(dialog).should().dismiss();
        BDDMockito.then(page).should().offDialog(handler.getValue());
    }

    @Test
    @DisplayName("확정 클릭 전에 뜬 안내 대화상자는 거부 판단 근거로 넘기지 않는다")
    void shouldExcludeDialogsShownBeforeFinalClick() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<Dialog>> handler = ArgumentCaptor.forClass(Consumer.class);
        willAnswer(invocation -> null).given(page).onDialog(handler.capture());
        Dialog notice = mock(Dialog.class);
        given(notice.message()).willReturn("예치금 잔액을 확인하세요");
        willAnswer(invocation -> {
            handler.getValue().accept(notice);
            return null;
        }).given(purchaseButton).click();

        PurchaseSubmission submission = new PlaywrightPurchaseExecutor(false).buy(page, user, 3, () -> false);

        then(submission.status()).isEqualTo(Status.CLICKED);
        then(submission.dialogMessages()).isEmpty();
    }

    @Test
    @DisplayName("클릭 뒤 페이지가 닫혀 대화상자 핸들러 해제가 실패해도 '클릭함' 결과를 잃지 않는다")
    void shouldKeepClickedResultWhenPageClosesAfterClick() {
        willThrow(new com.microsoft.playwright.PlaywrightException("Target closed")).given(page).offDialog(any());

        PurchaseSubmission submission = new PlaywrightPurchaseExecutor(false).buy(page, user, 3, () -> false);

        then(submission.status()).isEqualTo(Status.CLICKED);
    }
}
