package org.nowstart.lotto.adapter.out.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.CheckResult;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.LottoResult;
import org.nowstart.lotto.domain.exception.LedgerUnreadableException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PlaywrightResultExecutor {

    static final String EMPTY_LEDGER_TEXT = "조회 결과가 없습니다";
    /** Playwright의 visible 셀렉터 엔진: 앞 locator가 찾은 요소 중 보이는 것만 남긴다. */
    static final String VISIBLE_ONLY = "visible=true";
    private static final double LEDGER_POLL_INTERVAL_MS = 1_000;
    private static final double LEDGER_CONTENT_TIMEOUT_MS = 15_000;

    @Retryable(
            includes = PlaywrightException.class,
            maxRetriesString = "${lotto.max-retries:3}",
            delayString = "${lotto.retry-delay-ms:2000}"
    )
    public List<CheckResult> check(Page page) {
        log.info("[Check] Start");
        navigateResultTable(page);

        List<ResultRow> resultRows = loadResultRowsLenient(page);
        List<CheckResult> results = new ArrayList<>();
        int captureFailures = 0;
        Exception lastCaptureFailure = null;
        for (ResultRow resultRow : resultRows) {
            try {
                results.add(captureDetail(page, resultRow));
            } catch (Exception exception) {
                captureFailures++;
                lastCaptureFailure = exception;
                log.warn("[Check] Skip detail capture resultKey={}", resultRow.result().key(), exception);
            }
        }

        // 파싱 가능한 행이 있는데 상세 캡처가 전부 실패한 경우는 이상 신호이므로 실패로 처리한다.
        // 단, 일시적 오류(모달 대기/스크린샷 타임아웃 등)일 수 있으므로 재시도 대상인 PlaywrightException으로 던져
        // 설정된 재시도 정책을 우회하지 않도록 한다(원인 보존).
        if (!resultRows.isEmpty() && results.isEmpty()) {
            throw new PlaywrightException(
                    "결과 " + resultRows.size() + "행의 상세 캡처가 모두 실패했습니다 (selector 변경/일시 오류 의심)",
                    lastCaptureFailure);
        }

        log.info("[Check] Complete resultCount={} captureFailures={}", results.size(), captureFailures);
        return results;
    }

    /**
     * 원장을 fail-closed로 읽는다: 행을 하나라도 읽지 못하거나, 행도 빈 결과 표시도 없으면 판독 불가로 본다.
     * 구매 여부 판단에 쓰이므로 "읽지 못함"을 "0행"으로 바꾸지 않는다.
     */
    public List<LottoResult> readLedger(Page page) {
        navigateResultTable(page);
        waitForLedgerContent(page);
        return loadResultRowsStrict(page).stream().map(ResultRow::result).toList();
    }

    /**
     * NETWORKIDLE 이후에도 표가 비동기로 채워질 수 있으므로, 보이는 행이나 보이는 빈 결과 표시가 나타날 때까지 기다린다.
     * 둘 다 나타나지 않으면 "0행"이 아니라 판독 불가다.
     */
    private void waitForLedgerContent(Page page) {
        try {
            visibleRows(page).or(visibleEmptyMarker(page)).first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(LEDGER_CONTENT_TIMEOUT_MS));
        } catch (PlaywrightException exception) {
            throw new LedgerUnreadableException(
                    "원장 내용이 표시되지 않았습니다 (행도 '" + EMPTY_LEDGER_TEXT + "' 표시도 없음)", exception);
        }
    }

    private Locator visibleRows(Page page) {
        return page.locator(LottoBrowserConstants.RESULT_ROW).locator(VISIBLE_ONLY);
    }

    private Locator visibleEmptyMarker(Page page) {
        return page.getByText(EMPTY_LEDGER_TEXT).locator(VISIBLE_ONLY);
    }

    /** 조건을 만족할 때까지 원장을 다시 읽는다. 중간 판독 실패는 다음 읽기로 넘긴다. */
    public Optional<List<LottoResult>> awaitLedger(
            Page page,
            Predicate<List<LottoResult>> condition,
            Duration timeout,
            BooleanSupplier abortRequested
    ) {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        int attempt = 0;
        while (true) {
            attempt++;
            if (abortRequested.getAsBoolean()) {
                log.warn("[LedgerWait] Aborted attempt={}", attempt);
                return Optional.empty();
            }
            try {
                List<LottoResult> rows = readLedger(page);
                if (condition.test(rows)) {
                    log.info("[LedgerWait] Condition met attempt={} rowCount={}", attempt, rows.size());
                    return Optional.of(rows);
                }
                log.info("[LedgerWait] Not yet attempt={} rows={}", attempt, summarize(rows));
            } catch (LedgerUnreadableException | PlaywrightException exception) {
                log.warn("[LedgerWait] Read failed attempt={} cause={}", attempt, exception.getMessage());
            }
            long remainingMs = (deadlineNanos - System.nanoTime()) / 1_000_000L;
            if (remainingMs <= 0) {
                return Optional.empty();
            }
            try {
                page.waitForTimeout(Math.min(LEDGER_POLL_INTERVAL_MS, remainingMs));
            } catch (PlaywrightException exception) {
                // 페이지·브라우저가 죽었으면 더 읽을 수 없다. 호출자가 '확인 못 함'으로 판정한다.
                log.warn("[LedgerWait] Page unavailable - stop waiting attempt={}", attempt, exception);
                return Optional.empty();
            }
        }
    }

    /** 지정한 원장 행의 티켓 이미지를 캡처한다. 실패한 행은 이미지 없이 돌려준다. */
    public List<CheckResult> captureTickets(Page page, List<LottoResult> targets) {
        if (targets.isEmpty()) {
            return List.of();
        }
        Map<String, ResultRow> rowsByKey;
        try {
            navigateResultTable(page);
            rowsByKey = loadResultRowsLenient(page).stream()
                    .collect(Collectors.toMap(row -> row.result().key(), Function.identity(), (first, second) -> first));
        } catch (RuntimeException exception) {
            log.warn("[Ticket] Ledger reload failed - sending without images", exception);
            return targets.stream().map(target -> new CheckResult(target, null)).toList();
        }

        List<CheckResult> results = new ArrayList<>();
        for (LottoResult target : targets) {
            ResultRow row = rowsByKey.get(target.key());
            if (row == null) {
                log.warn("[Ticket] Row not found on reload key={}", target.key());
                results.add(new CheckResult(target, null));
                continue;
            }
            try {
                results.add(captureDetail(page, row));
            } catch (RuntimeException exception) {
                log.warn("[Ticket] Capture failed key={}", target.key(), exception);
                results.add(new CheckResult(target, null));
            }
        }
        return results;
    }

    private void navigateResultTable(Page page) {
        page.navigate(LottoBrowserConstants.RESULT_TABLE);
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    private List<ResultRow> loadResultRowsStrict(Page page) {
        Locator rows = page.locator(LottoBrowserConstants.RESULT_ROW);
        int rowCount = rows.count();
        List<ResultRow> resultRows = new ArrayList<>();
        boolean emptyMarkerSeen = false;
        for (int index = 0; index < rowCount; index++) {
            Locator row = rows.nth(index);
            try {
                if (row.innerText().contains(EMPTY_LEDGER_TEXT)) {
                    emptyMarkerSeen = true;
                    continue;
                }
                resultRows.add(new ResultRow(row, extractResult(row)));
            } catch (PlaywrightException exception) {
                throw new LedgerUnreadableException("원장 " + (index + 1) + "번째 행을 읽지 못했습니다", exception);
            }
        }
        if (resultRows.isEmpty() && !emptyMarkerSeen && !hasEmptyMarkerOnPage(page)) {
            throw new LedgerUnreadableException(
                    "원장을 판독하지 못했습니다: 행도 '" + EMPTY_LEDGER_TEXT + "' 표시도 없습니다 (로그인 풀림/로딩 실패 의심)");
        }
        return resultRows;
    }

    /** 결과 확인용: 읽지 못한 행은 건너뛴다(구매 판단에는 쓰지 않는다). */
    private List<ResultRow> loadResultRowsLenient(Page page) {
        Locator rows = page.locator(LottoBrowserConstants.RESULT_ROW);
        int rowCount = rows.count();
        if (rowCount == 0) {
            log.info("[Check] No rows found");
            return List.of();
        }

        List<ResultRow> resultRows = new ArrayList<>();
        for (int index = 0; index < rowCount; index++) {
            try {
                Locator row = rows.nth(index);
                if (row.innerText().contains(EMPTY_LEDGER_TEXT)) {
                    continue;
                }
                resultRows.add(new ResultRow(row, extractResult(row)));
            } catch (Exception exception) {
                log.warn("[Check] Skip invalid row index={}", index, exception);
            }
        }
        return resultRows;
    }

    /** 숨겨진 템플릿 문구는 세지 않는다(보이는 빈 결과 표시만 인정). */
    private boolean hasEmptyMarkerOnPage(Page page) {
        try {
            return visibleEmptyMarker(page).count() > 0;
        } catch (PlaywrightException exception) {
            return false;
        }
    }

    private LottoResult extractResult(Locator row) {
        Locator numberLocator = row.locator(LottoBrowserConstants.RESULT_COL_NUMBER);
        return new LottoResult(
                row.locator(LottoBrowserConstants.RESULT_COL_DATE1).innerText().trim(),
                row.locator(LottoBrowserConstants.RESULT_COL_ROUND).innerText().trim(),
                row.locator(LottoBrowserConstants.RESULT_COL_NAME).innerText().trim(),
                numberLocator.innerText().trim().replace(" ", ""),
                row.locator(LottoBrowserConstants.RESULT_COL_COUNT).innerText().trim(),
                row.locator(LottoBrowserConstants.RESULT_COL_RESULT).innerText().trim(),
                row.locator(LottoBrowserConstants.RESULT_COL_PRICE).innerText().trim()
        );
    }

    private CheckResult captureDetail(Page page, ResultRow resultRow) {
        Locator numberLocator = resultRow.row().locator(LottoBrowserConstants.RESULT_COL_NUMBER);
        numberLocator.click();

        Locator modalLocator = page.locator(LottoBrowserConstants.DETAIL_MODAL);
        modalLocator.waitFor();
        byte[] imageBytes = modalLocator.screenshot();
        page.click(LottoBrowserConstants.CLOSE_DETAIL_BTN);

        return new CheckResult(resultRow.result(), imageBytes);
    }

    private String summarize(List<LottoResult> rows) {
        return rows.stream()
                .map(row -> row.name() + "/" + row.round() + "회/" + row.count() + "장/" + row.result() + "/" + row.date())
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private record ResultRow(Locator row, LottoResult result) {
    }
}
