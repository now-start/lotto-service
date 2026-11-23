package org.nowstart.lotto.adapter.outbound.browser

import com.microsoft.playwright.Dialog
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.PlaywrightException
import com.microsoft.playwright.TimeoutError
import com.microsoft.playwright.options.WaitForSelectorState
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.nowstart.lotto.application.port.outbound.LottoUser
import org.nowstart.lotto.application.port.outbound.PurchaseSubmission.Status
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.resilience.annotation.EnableResilientMethods
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig

class PlaywrightPurchaseExecutorTest {

    private val user = LottoUser("user1", "password", 5, "user1@nowstart.org", false)
    private val page = mockk<Page>(relaxed = true)
    private val purchaseButton = mockk<Locator>(relaxed = true)
    private val finalConfirm = mockk<Locator>(relaxed = true)
    private val dialogHandler = slot<Consumer<Dialog>>()

    init {
        every { page.locator(LottoBrowserConstants.PURCHASE_BTN) } returns purchaseButton
        every { page.locator(LottoBrowserConstants.FINAL_CONFIRM_BTN) } returns finalConfirm
        every { page.onDialog(capture(dialogHandler)) } returns Unit
    }

    @Test
    fun `dry-run이면 최종 확정 직전에 멈추고 클릭하지 않는다`() {
        assertThat(executor(dryRun = true).buy(page, user, 3) { false }.status).isEqualTo(Status.DRY_RUN)
        verify(exactly = 0) { finalConfirm.click(any<Locator.ClickOptions>()) }
    }

    @Test
    fun `중단 요청이 있으면 확정 클릭 전에 멈춘다`() {
        assertThat(executor().buy(page, user, 3) { true }.status).isEqualTo(Status.NOT_CLICKED)
        verify(exactly = 0) { finalConfirm.click(any<Locator.ClickOptions>()) }
    }

    @Test
    fun `확정 클릭이 예외로 끝나도 클릭함으로 돌려준다 - 요청이 이미 전송됐을 수 있어 원장으로 판정해야 한다`() {
        every { finalConfirm.click(any<Locator.ClickOptions>()) } throws TimeoutError("click timeout")
        every { finalConfirm.waitFor(match { it.state == WaitForSelectorState.HIDDEN }) } throws TimeoutError("still visible")

        assertThat(executor().buy(page, user, 3) { false }.status).isEqualTo(Status.CLICKED)
    }

    @Test
    fun `확정 클릭 이후 뜬 대화상자 문구만 모아 돌려준다`() {
        every { purchaseButton.click() } answers { dialogHandler.captured.accept(dialog("예치금 잔액을 확인하세요")) }
        val rejection = dialog("이번 회차 구매한도를 초과했습니다")
        every { finalConfirm.click(any<Locator.ClickOptions>()) } answers { dialogHandler.captured.accept(rejection) }

        val submission = executor().buy(page, user, 3) { false }

        assertThat(submission.status).isEqualTo(Status.CLICKED)
        assertThat(submission.dialogMessages).containsExactly("이번 회차 구매한도를 초과했습니다")
        verify { rejection.dismiss() }
        verify { page.offDialog(dialogHandler.captured) }
    }

    @Test
    fun `클릭 뒤 페이지가 닫혀 대화상자 핸들러 해제가 실패해도 클릭함 결과를 잃지 않는다`() {
        every { page.offDialog(any()) } throws PlaywrightException("Target closed")

        assertThat(executor().buy(page, user, 3) { false }.status).isEqualTo(Status.CLICKED)
    }

    private fun executor(dryRun: Boolean = false) = PlaywrightPurchaseExecutor(BrowserSettings(1, 1_000, dryRun))

    private fun dialog(message: String): Dialog = mockk(relaxed = true) {
        every { message() } returns message
        every { type() } returns "alert"
    }
}

@SpringJUnitConfig(classes = [PlaywrightPurchaseExecutorRetryTest.Config::class])
@TestPropertySource(properties = ["lotto.max-retries=3", "lotto.retry-delay-ms=1"])
class PlaywrightPurchaseExecutorRetryTest {

    @Autowired
    private lateinit var executor: PlaywrightPurchaseExecutor

    @Test
    fun `구매는 실패해도 재시도하지 않고 예외를 전파한다 (멱등하지 않은 실거래 보호)`() {
        val page = mockk<Page>(relaxed = true)
        val autoNumber = mockk<Locator>()
        val attempts = AtomicInteger()
        every { page.locator(LottoBrowserConstants.AUTO_NUMBER) } returns autoNumber
        every { autoNumber.click() } answers {
            attempts.incrementAndGet()
            throw IllegalStateException("temporary")
        }

        assertThatThrownBy { executor.buy(page, LottoUser("user1", "password", 2, "user1@nowstart.org", false), 2) { false } }
            .isInstanceOf(IllegalStateException::class.java)
        assertThat(attempts.get()).isEqualTo(1)
        verify(exactly = 1) { page.navigate(LottoBrowserConstants.URL_PURCHASE) }
    }

    @Configuration
    @EnableResilientMethods
    class Config {

        @Bean
        fun playwrightPurchaseExecutor() = PlaywrightPurchaseExecutor(BrowserSettings(1, 1_000, dryRun = false))
    }
}
