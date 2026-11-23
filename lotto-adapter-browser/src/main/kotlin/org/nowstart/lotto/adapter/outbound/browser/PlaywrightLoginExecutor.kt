package org.nowstart.lotto.adapter.outbound.browser

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.PlaywrightException
import com.microsoft.playwright.options.AriaRole
import com.microsoft.playwright.options.LoadState
import com.microsoft.playwright.options.WaitForSelectorState
import org.nowstart.lotto.application.port.outbound.LottoAccountSnapshot
import org.nowstart.lotto.application.port.outbound.LottoUser
import org.slf4j.LoggerFactory
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Component

private val log = LoggerFactory.getLogger(PlaywrightLoginExecutor::class.java)

@Component
class PlaywrightLoginExecutor {

    @Retryable(
        includes = [PlaywrightException::class],
        maxRetriesString = "\${lotto.max-retries:3}",
        delayString = "\${lotto.retry-delay-ms:2000}",
    )
    fun login(page: Page, user: LottoUser): LottoAccountSnapshot {
        log.info("[Login][{}] Start", user.id)
        page.navigate(LottoBrowserConstants.URL_LOGIN)

        val idInput = page.getByPlaceholder(LottoBrowserConstants.ID_INPUT)
        waitForLoginFormOrSkip(idInput)
        if (idInput.isVisible) {
            idInput.fill(user.id)
            page.getByPlaceholder(LottoBrowserConstants.PASSWORD_INPUT).fill(user.password)
            page.click(LottoBrowserConstants.LOGIN_LINK)
        }

        page.waitForLoadState(LoadState.NETWORKIDLE)

        val changeLaterButton = page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName(LottoBrowserConstants.CHANGE_LATER))
        if (changeLaterButton.isVisible) {
            changeLaterButton.click()
            page.waitForLoadState(LoadState.NETWORKIDLE)
        }

        val snapshot = readAccount(page)
        log.info("[Login][{}] Success deposit={}", user.id, snapshot.deposit)
        return snapshot
    }

    /** 마이페이지에서 이름과 예치금을 읽는다. 구매 후 예치금 차감 확인에도 쓴다. */
    fun readAccount(page: Page): LottoAccountSnapshot {
        page.navigate(LottoBrowserConstants.URL_MY_PAGE)
        return LottoAccountSnapshot(
            page.locator(LottoBrowserConstants.USER_NAME).innerText(),
            page.locator(LottoBrowserConstants.USER_DEPOSIT).innerText(),
        )
    }

    private fun waitForLoginFormOrSkip(idInput: Locator) {
        try {
            idInput.waitFor(Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(5_000.0))
        } catch (exception: PlaywrightException) {
            // 로그인 폼이 렌더링되지 않으면(이미 로그인 상태 등) 채우기 단계를 건너뛴다.
            log.debug("로그인 폼이 노출되지 않아 입력을 건너뜁니다", exception)
        }
    }
}
