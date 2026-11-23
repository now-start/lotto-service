package org.nowstart.lotto.adapter.outbound.browser

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.PlaywrightException
import com.microsoft.playwright.TimeoutError
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.nowstart.lotto.application.port.outbound.LottoResult
import org.nowstart.lotto.domain.exception.LedgerUnreadableException

class PlaywrightResultExecutorTest {

    private val executor = PlaywrightResultExecutor()
    private val page = mockk<Page>(relaxed = true)
    private val rows = mockk<Locator>(relaxed = true)
    private val pageMarker = mockk<Locator>(relaxed = true)
    private val visibleMarker = mockk<Locator>(relaxed = true)
    private val firstContent = mockk<Locator>(relaxed = true)

    init {
        val visibleRows = mockk<Locator>(relaxed = true)
        val rowOrMarker = mockk<Locator>(relaxed = true)
        every { page.locator(LottoBrowserConstants.RESULT_ROW) } returns rows
        every { rows.locator(PlaywrightResultExecutor.VISIBLE_ONLY) } returns visibleRows
        every { page.getByText(PlaywrightResultExecutor.EMPTY_LEDGER_TEXT) } returns pageMarker
        every { pageMarker.locator(PlaywrightResultExecutor.VISIBLE_ONLY) } returns visibleMarker
        every { visibleRows.or(visibleMarker) } returns rowOrMarker
        every { rowOrMarker.first() } returns firstContent
        every { rows.count() } returns 0
        every { visibleMarker.count() } returns 0
    }

    @Test
    fun `보이는 행도 빈 결과 표시도 나타나지 않으면 판독 불가로 본다 (표가 늦게 채워지는 경우)`() {
        every { firstContent.waitFor(any()) } throws TimeoutError("Timeout 15000ms exceeded")

        assertThatThrownBy { executor.readLedger(page) }.isInstanceOf(LedgerUnreadableException::class.java)
    }

    @Test
    fun `행도 빈 결과 표시도 없으면 0행으로 보지 않고 판독 불가로 본다 (로그인 풀림 로딩 실패)`() {
        assertThatThrownBy { executor.readLedger(page) }.isInstanceOf(LedgerUnreadableException::class.java)
    }

    @Test
    fun `빈 결과 표시 행이 있으면 0행으로 인정한다 (신규 계정)`() {
        val row = mockk<Locator> { every { innerText() } returns "조회 결과가 없습니다." }
        every { rows.count() } returns 1
        every { rows.nth(0) } returns row

        assertThat(executor.readLedger(page)).isEmpty()
    }

    @Test
    fun `보이는 빈 결과 표시가 행이 아닌 곳에 있어도 0행으로 인정한다`() {
        every { visibleMarker.count() } returns 1

        assertThat(executor.readLedger(page)).isEmpty()
    }

    @Test
    fun `숨겨진 빈 결과 문구만 있으면 0행으로 인정하지 않는다`() {
        every { pageMarker.count() } returns 1

        assertThatThrownBy { executor.readLedger(page) }.isInstanceOf(LedgerUnreadableException::class.java)
    }

    @Test
    fun `행을 하나라도 읽지 못하면 건너뛰지 않고 판독 불가로 본다`() {
        val row = mockk<Locator> { every { innerText() } throws PlaywrightException("element detached") }
        every { rows.count() } returns 1
        every { rows.nth(0) } returns row

        assertThatThrownBy { executor.readLedger(page) }.isInstanceOf(LedgerUnreadableException::class.java)
    }

    @Test
    fun `원장 행의 각 칸을 읽는다`() {
        val row = mockk<Locator>()
        every { row.innerText() } returns "2026-11-22 로또6/45 1252 ..."
        mapOf(
            LottoBrowserConstants.RESULT_COL_DATE1 to "2026-11-22",
            LottoBrowserConstants.RESULT_COL_ROUND to "1252",
            LottoBrowserConstants.RESULT_COL_NAME to "로또6/45",
            LottoBrowserConstants.RESULT_COL_NUMBER to "12345 67890",
            LottoBrowserConstants.RESULT_COL_COUNT to "5",
            LottoBrowserConstants.RESULT_COL_RESULT to "미추첨",
            LottoBrowserConstants.RESULT_COL_PRICE to "5,000",
        ).forEach { (selector, text) -> every { row.locator(selector).innerText() } returns text }
        every { rows.count() } returns 1
        every { rows.nth(0) } returns row

        assertThat(executor.readLedger(page))
            .containsExactly(LottoResult("2026-11-22", "1252", "로또6/45", "1234567890", "5", "미추첨", "5,000"))
    }
}
