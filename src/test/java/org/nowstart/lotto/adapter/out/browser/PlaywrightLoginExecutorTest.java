package org.nowstart.lotto.adapter.out.browser;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort.LottoUser;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.LottoAccountSnapshot;

@DisplayName("Playwright 로그인")
class PlaywrightLoginExecutorTest {

    private final PlaywrightLoginExecutor playwrightLoginExecutor = new PlaywrightLoginExecutor();

    @Test
    @DisplayName("로그인 후 비밀번호 변경 안내가 나오면 다음에 변경 버튼을 클릭한다")
    void shouldSkipPasswordChangeNoticeAfterLogin() {
        Page page = mock(Page.class);
        Locator idInput = mock(Locator.class);
        Locator passwordInput = mock(Locator.class);
        Locator changeLaterButton = mock(Locator.class);
        Locator userName = mock(Locator.class);
        Locator userDeposit = mock(Locator.class);
        LottoUser user = new LottoUser("user1", "password", 1, "user1@nowstart.org", false);

        given(page.getByPlaceholder(LottoBrowserConstants.ID_INPUT)).willReturn(idInput);
        given(idInput.isVisible()).willReturn(true);
        given(page.getByPlaceholder(LottoBrowserConstants.PASSWORD_INPUT)).willReturn(passwordInput);
        given(page.getByRole(eq(AriaRole.BUTTON), any(Page.GetByRoleOptions.class)))
                .willReturn(changeLaterButton);
        given(changeLaterButton.isVisible()).willReturn(true);
        given(page.locator(LottoBrowserConstants.USER_NAME)).willReturn(userName);
        given(page.locator(LottoBrowserConstants.USER_DEPOSIT)).willReturn(userDeposit);
        given(userName.innerText()).willReturn("홍길동");
        given(userDeposit.innerText()).willReturn("10,000원");

        LottoAccountSnapshot snapshot = playwrightLoginExecutor.login(page, user);

        ArgumentCaptor<Page.GetByRoleOptions> optionsCaptor =
                ArgumentCaptor.forClass(Page.GetByRoleOptions.class);
        InOrder noticeHandling = inOrder(page, changeLaterButton);
        noticeHandling.verify(page).waitForLoadState(LoadState.NETWORKIDLE);
        noticeHandling.verify(page).getByRole(eq(AriaRole.BUTTON), optionsCaptor.capture());
        noticeHandling.verify(changeLaterButton).isVisible();
        noticeHandling.verify(changeLaterButton).click();
        noticeHandling.verify(page).waitForLoadState(LoadState.NETWORKIDLE);

        then(optionsCaptor.getValue().name).isEqualTo(LottoBrowserConstants.CHANGE_LATER);
        then(snapshot.name()).isEqualTo("홍길동");
        then(snapshot.deposit()).isEqualTo("10,000원");
    }
}
