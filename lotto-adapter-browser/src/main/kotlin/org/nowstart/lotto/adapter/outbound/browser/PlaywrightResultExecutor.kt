package org.nowstart.lotto.adapter.outbound.browser

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.PlaywrightException
import com.microsoft.playwright.options.LoadState
import com.microsoft.playwright.options.WaitForSelectorState
import java.time.Duration
import org.nowstart.lotto.application.port.outbound.CheckResult
import org.nowstart.lotto.application.port.outbound.LottoResult
import org.nowstart.lotto.domain.exception.LedgerUnreadableException
import org.slf4j.LoggerFactory
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Component

private val log = LoggerFactory.getLogger(PlaywrightResultExecutor::class.java)

@Component
class PlaywrightResultExecutor {

    @Retryable(
        includes = [PlaywrightException::class],
        maxRetriesString = "\${lotto.max-retries:3}",
        delayString = "\${lotto.retry-delay-ms:2000}",
    )
    fun check(page: Page): List<CheckResult> {
        log.info("[Check] Start")
        navigateResultTable(page)

        val resultRows = loadResultRowsLenient(page)
        val results = mutableListOf<CheckResult>()
        var lastCaptureFailure: Exception? = null
        for (resultRow in resultRows) {
            try {
                results += captureDetail(page, resultRow)
            } catch (exception: Exception) {
                lastCaptureFailure = exception
                log.warn("[Check] Skip detail capture resultKey={}", resultRow.result.key, exception)
            }
        }

        // 파싱 가능한 행이 있는데 상세 캡처가 전부 실패한 경우는 이상 신호이므로 실패로 처리한다.
        // 일시적 오류일 수 있으므로 재시도 대상인 PlaywrightException으로 던져 재시도 정책을 우회하지 않는다(원인 보존).
        if (resultRows.isNotEmpty() && results.isEmpty()) {
            throw PlaywrightException(
                "결과 ${resultRows.size}행의 상세 캡처가 모두 실패했습니다 (selector 변경/일시 오류 의심)",
                lastCaptureFailure,
            )
        }

        log.info("[Check] Complete resultCount={} captureFailures={}", results.size, resultRows.size - results.size)
        return results
    }

    /**
     * 원장을 fail-closed로 읽는다: 행을 하나라도 읽지 못하거나, 행도 빈 결과 표시도 없으면 판독 불가로 본다.
     * 구매 여부 판단에 쓰이므로 "읽지 못함"을 "0행"으로 바꾸지 않는다.
     */
    fun readLedger(page: Page): List<LottoResult> {
        navigateResultTable(page)
        waitForLedgerContent(page)
        return loadResultRowsStrict(page).map { it.result }
    }

    /** 조건을 만족할 때까지 원장을 다시 읽는다. 중간 판독 실패는 다음 읽기로 넘긴다. */
    fun awaitLedger(
        page: Page,
        condition: (List<LottoResult>) -> Boolean,
        timeout: Duration,
        abortRequested: () -> Boolean,
    ): List<LottoResult>? {
        val deadlineNanos = System.nanoTime() + timeout.toNanos()
        var attempt = 0
        while (true) {
            attempt++
            if (abortRequested()) {
                log.warn("[LedgerWait] Aborted attempt={}", attempt)
                return null
            }
            try {
                val rows = readLedger(page)
                if (condition(rows)) {
                    log.info("[LedgerWait] Condition met attempt={} rowCount={}", attempt, rows.size)
                    return rows
                }
                log.info("[LedgerWait] Not yet attempt={} rows={}", attempt, summarize(rows))
            } catch (exception: LedgerUnreadableException) {
                log.warn("[LedgerWait] Read failed attempt={} cause={}", attempt, exception.message)
            } catch (exception: PlaywrightException) {
                log.warn("[LedgerWait] Read failed attempt={} cause={}", attempt, exception.message)
            }
            val remainingMs = (deadlineNanos - System.nanoTime()) / 1_000_000L
            if (remainingMs <= 0) {
                return null
            }
            try {
                page.waitForTimeout(minOf(LEDGER_POLL_INTERVAL_MS, remainingMs.toDouble()))
            } catch (exception: PlaywrightException) {
                // 페이지·브라우저가 죽었으면 더 읽을 수 없다. 호출자가 '확인 못 함'으로 판정한다.
                log.warn("[LedgerWait] Page unavailable - stop waiting attempt={}", attempt, exception)
                return null
            }
        }
    }

    /** 지정한 원장 행의 티켓 이미지를 캡처한다. 실패한 행은 이미지 없이 돌려준다. */
    fun captureTickets(page: Page, targets: List<LottoResult>): List<CheckResult> {
        if (targets.isEmpty()) {
            return emptyList()
        }
        val rowsByKey = try {
            navigateResultTable(page)
            loadResultRowsLenient(page).associateBy { it.result.key }
        } catch (exception: RuntimeException) {
            log.warn("[Ticket] Ledger reload failed - sending without images", exception)
            return targets.map { CheckResult(it, null) }
        }
        return targets.map { target ->
            val row = rowsByKey[target.key]
            if (row == null) {
                log.warn("[Ticket] Row not found on reload key={}", target.key)
                CheckResult(target, null)
            } else {
                try {
                    captureDetail(page, row)
                } catch (exception: RuntimeException) {
                    log.warn("[Ticket] Capture failed key={}", target.key, exception)
                    CheckResult(target, null)
                }
            }
        }
    }

    private fun navigateResultTable(page: Page) {
        page.navigate(LottoBrowserConstants.RESULT_TABLE)
        page.waitForLoadState(LoadState.NETWORKIDLE)
    }

    /**
     * NETWORKIDLE 이후에도 표가 비동기로 채워질 수 있으므로, 보이는 행이나 보이는 빈 결과 표시가 나타날 때까지 기다린다.
     * 둘 다 나타나지 않으면 "0행"이 아니라 판독 불가다.
     */
    private fun waitForLedgerContent(page: Page) {
        try {
            visibleRows(page).or(visibleEmptyMarker(page)).first().waitFor(
                Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(LEDGER_CONTENT_TIMEOUT_MS),
            )
        } catch (exception: PlaywrightException) {
            throw LedgerUnreadableException("원장 내용이 표시되지 않았습니다 (행도 '$EMPTY_LEDGER_TEXT' 표시도 없음)", exception)
        }
    }

    private fun visibleRows(page: Page): Locator = page.locator(LottoBrowserConstants.RESULT_ROW).locator(VISIBLE_ONLY)

    private fun visibleEmptyMarker(page: Page): Locator = page.getByText(EMPTY_LEDGER_TEXT).locator(VISIBLE_ONLY)

    private fun loadResultRowsStrict(page: Page): List<ResultRow> {
        val rows = page.locator(LottoBrowserConstants.RESULT_ROW)
        val resultRows = mutableListOf<ResultRow>()
        var emptyMarkerSeen = false
        for (index in 0 until rows.count()) {
            val row = rows.nth(index)
            try {
                if (row.innerText().contains(EMPTY_LEDGER_TEXT)) {
                    emptyMarkerSeen = true
                    continue
                }
                resultRows += ResultRow(row, extractResult(row))
            } catch (exception: PlaywrightException) {
                throw LedgerUnreadableException("원장 ${index + 1}번째 행을 읽지 못했습니다", exception)
            }
        }
        if (resultRows.isEmpty() && !emptyMarkerSeen && !hasEmptyMarkerOnPage(page)) {
            throw LedgerUnreadableException("원장을 판독하지 못했습니다: 행도 '$EMPTY_LEDGER_TEXT' 표시도 없습니다 (로그인 풀림/로딩 실패 의심)")
        }
        return resultRows
    }

    /** 결과 확인용: 읽지 못한 행은 건너뛴다(구매 판단에는 쓰지 않는다). */
    private fun loadResultRowsLenient(page: Page): List<ResultRow> {
        val rows = page.locator(LottoBrowserConstants.RESULT_ROW)
        val rowCount = rows.count()
        if (rowCount == 0) {
            log.info("[Check] No rows found")
            return emptyList()
        }
        return (0 until rowCount).mapNotNull { index ->
            try {
                val row = rows.nth(index)
                if (row.innerText().contains(EMPTY_LEDGER_TEXT)) null else ResultRow(row, extractResult(row))
            } catch (exception: Exception) {
                log.warn("[Check] Skip invalid row index={}", index, exception)
                null
            }
        }
    }

    /** 숨겨진 템플릿 문구는 세지 않는다(보이는 빈 결과 표시만 인정). */
    private fun hasEmptyMarkerOnPage(page: Page): Boolean = try {
        visibleEmptyMarker(page).count() > 0
    } catch (exception: PlaywrightException) {
        false
    }

    private fun extractResult(row: Locator): LottoResult = LottoResult(
        row.locator(LottoBrowserConstants.RESULT_COL_DATE1).innerText().trim(),
        row.locator(LottoBrowserConstants.RESULT_COL_ROUND).innerText().trim(),
        row.locator(LottoBrowserConstants.RESULT_COL_NAME).innerText().trim(),
        row.locator(LottoBrowserConstants.RESULT_COL_NUMBER).innerText().trim().replace(" ", ""),
        row.locator(LottoBrowserConstants.RESULT_COL_COUNT).innerText().trim(),
        row.locator(LottoBrowserConstants.RESULT_COL_RESULT).innerText().trim(),
        row.locator(LottoBrowserConstants.RESULT_COL_PRICE).innerText().trim(),
    )

    private fun captureDetail(page: Page, resultRow: ResultRow): CheckResult {
        resultRow.row.locator(LottoBrowserConstants.RESULT_COL_NUMBER).click()
        val modalLocator = page.locator(LottoBrowserConstants.DETAIL_MODAL)
        modalLocator.waitFor()
        val imageBytes = modalLocator.screenshot()
        page.click(LottoBrowserConstants.CLOSE_DETAIL_BTN)
        return CheckResult(resultRow.result, imageBytes)
    }

    private fun summarize(rows: List<LottoResult>): String =
        rows.joinToString(", ", "[", "]") { "${it.name}/${it.round}회/${it.count}장/${it.result}/${it.date}" }

    private class ResultRow(val row: Locator, val result: LottoResult)

    companion object {
        const val EMPTY_LEDGER_TEXT = "조회 결과가 없습니다"

        /** Playwright의 visible 셀렉터 엔진: 앞 locator가 찾은 요소 중 보이는 것만 남긴다. */
        const val VISIBLE_ONLY = "visible=true"

        private const val LEDGER_POLL_INTERVAL_MS = 1_000.0
        private const val LEDGER_CONTENT_TIMEOUT_MS = 15_000.0
    }
}
