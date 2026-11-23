package org.nowstart.lotto.adapter.outbound.browser

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.TimeoutError
import com.microsoft.playwright.options.AriaRole
import com.microsoft.playwright.options.LoadState
import com.microsoft.playwright.options.WaitForSelectorState
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.nowstart.lotto.application.port.outbound.UserAccount
import org.nowstart.lotto.domain.exception.LoginRejectedException

class PlaywrightLoginExecutorTest {

    @Test
    fun `로그인 후 비밀번호 변경 안내가 나오면 다음에 변경 버튼을 클릭한다`() {
        val page = mockk<Page>(relaxed = true)
        val idInput = mockk<Locator>(relaxed = true)
        val changeLater = mockk<Locator>(relaxed = true)
        val options = slot<Page.GetByRoleOptions>()
        every { page.getByPlaceholder(BrowserConstants.ID_INPUT) } returns idInput
        // 제출 전에는 폼이 보이고, 로그인에 성공하면 사라진다
        every { idInput.isVisible } returnsMany listOf(true, false)
        every { page.getByRole(AriaRole.BUTTON, capture(options)) } returns changeLater
        every { changeLater.isVisible } returns true
        every { page.locator(BrowserConstants.USER_NAME).innerText() } returns "홍길동"
        every { page.locator(BrowserConstants.USER_DEPOSIT).innerText() } returns "10,000원"

        val snapshot = PlaywrightLoginExecutor().login(page, UserAccount("user1", "password", 1, "user1@nowstart.org", false))

        verifyOrder {
            page.waitForLoadState(LoadState.NETWORKIDLE)
            changeLater.click()
            page.waitForLoadState(LoadState.NETWORKIDLE)
        }
        assertThat(options.captured.name).isEqualTo(BrowserConstants.CHANGE_LATER)
        assertThat(snapshot.name).isEqualTo("홍길동")
        assertThat(snapshot.deposit).isEqualTo("10,000원")
    }

    @Test
    fun `제출 후에도 로그인 폼이 그대로면 로그인 거부로 본다 (사이트 장애와 구분)`() {
        val page = mockk<Page>(relaxed = true)
        val idInput = mockk<Locator>(relaxed = true)
        every { page.getByPlaceholder(BrowserConstants.ID_INPUT) } returns idInput
        every { idInput.isVisible } returns true
        every { idInput.waitFor(match { it.state == WaitForSelectorState.HIDDEN }) } throws TimeoutError("form still visible")

        assertThatThrownBy { PlaywrightLoginExecutor().login(page, UserAccount("user1", "wrong", 1, "user1@nowstart.org", false)) }
            .isInstanceOf(LoginRejectedException::class.java)
    }
}
