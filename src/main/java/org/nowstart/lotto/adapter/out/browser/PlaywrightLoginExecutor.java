package org.nowstart.lotto.adapter.out.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.LottoAccountSnapshot;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort.LottoUser;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PlaywrightLoginExecutor {

    @Retryable(
            includes = PlaywrightException.class,
            maxRetriesString = "${lotto.max-retries:3}",
            delayString = "${lotto.retry-delay-ms:2000}"
    )
    public LottoAccountSnapshot login(Page page, LottoUser user) {
        log.info("[Login][{}] Start", user.id());
        page.navigate(LottoBrowserConstants.URL_LOGIN);

        Locator idInput = page.getByPlaceholder(LottoBrowserConstants.ID_INPUT);
        waitForLoginFormOrSkip(idInput);
        if (idInput.isVisible()) {
            idInput.fill(user.id());
            page.getByPlaceholder(LottoBrowserConstants.PASSWORD_INPUT).fill(user.password());
            page.click(LottoBrowserConstants.LOGIN_LINK);
        }

        Locator changeLaterLink = page.getByRole(
                AriaRole.LINK,
                new Page.GetByRoleOptions().setName(LottoBrowserConstants.CHANGE_LATER)
        );
        if (changeLaterLink.isVisible()) {
            changeLaterLink.click();
        }

        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.navigate(LottoBrowserConstants.URL_MY_PAGE);

        LottoAccountSnapshot snapshot = new LottoAccountSnapshot(
                page.locator(LottoBrowserConstants.USER_NAME).innerText(),
                page.locator(LottoBrowserConstants.USER_DEPOSIT).innerText()
        );

        log.info("[Login][{}] Success deposit={}", user.id(), snapshot.deposit());
        return snapshot;
    }

    private void waitForLoginFormOrSkip(Locator idInput) {
        try {
            idInput.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(5_000));
        } catch (PlaywrightException exception) {
            // 로그인 폼이 렌더링되지 않으면(이미 로그인 상태 등) 채우기 단계를 건너뛴다.
            log.debug("로그인 폼이 노출되지 않아 입력을 건너뜁니다", exception);
        }
    }
}
