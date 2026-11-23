package org.nowstart.lotto.adapter.outbound.browser

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import com.microsoft.playwright.options.LoadState
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.nowstart.lotto.application.port.outbound.LottoUser

class PlaywrightLoginExecutorTest {

    @Test
    fun `로그인 후 비밀번호 변경 안내가 나오면 다음에 변경 버튼을 클릭한다`() {
        val page = mockk<Page>(relaxed = true)
        val idInput = mockk<Locator>(relaxed = true)
        val changeLater = mockk<Locator>(relaxed = true)
        val options = slot<Page.GetByRoleOptions>()
        every { page.getByPlaceholder(LottoBrowserConstants.ID_INPUT) } returns idInput
        every { idInput.isVisible } returns true
        every { page.getByRole(AriaRole.BUTTON, capture(options)) } returns changeLater
        every { changeLater.isVisible } returns true
        every { page.locator(LottoBrowserConstants.USER_NAME).innerText() } returns "홍길동"
        every { page.locator(LottoBrowserConstants.USER_DEPOSIT).innerText() } returns "10,000원"

        val snapshot = PlaywrightLoginExecutor().login(page, LottoUser("user1", "password", 1, "user1@nowstart.org", false))

        verifyOrder {
            page.waitForLoadState(LoadState.NETWORKIDLE)
            changeLater.click()
            page.waitForLoadState(LoadState.NETWORKIDLE)
        }
        assertThat(options.captured.name).isEqualTo(LottoBrowserConstants.CHANGE_LATER)
        assertThat(snapshot.name).isEqualTo("홍길동")
        assertThat(snapshot.deposit).isEqualTo("10,000원")
    }
}
