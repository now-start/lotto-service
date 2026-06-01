package org.nowstart.lotto.adapter.out.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.CheckResult;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.PurchaseReceipt;
import org.nowstart.lotto.config.LottoProperties;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.LottoResult;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PlaywrightResultExecutor {

    private final LottoProperties lottoProperties;

    public PlaywrightResultExecutor(LottoProperties lottoProperties) {
        this.lottoProperties = lottoProperties;
    }

    @Retryable(
            includes = Exception.class,
            maxRetriesString = "${lotto.max-retries:3}",
            delayString = "${lotto.retry-delay-ms:2000}"
    )
    public List<CheckResult> check(Page page) {
        log.info("[Check] Start");
        navigateResultTable(page);

        List<ResultRow> resultRows = loadResultRows(page);
        List<CheckResult> results = new ArrayList<>();
        for (ResultRow resultRow : resultRows) {
            try {
                results.add(captureDetail(page, resultRow));
            } catch (Exception exception) {
                log.warn("[Check] Skip detail capture resultKey={}",
                        LottoPurchaseResultSelector.key(resultRow.result()), exception);
            }
        }

        log.info("[Check] Complete resultCount={}", results.size());
        return results;
    }

    @Retryable(
            includes = Exception.class,
            maxRetriesString = "${lotto.max-retries:3}",
            delayString = "${lotto.retry-delay-ms:2000}"
    )
    public CheckResult check(
            Page page,
            PurchaseReceipt purchaseReceipt
    ) {
        log.info("[PurchaseCheck] Start count={} timeoutMs={} pollIntervalMs={}",
                purchaseReceipt.count(),
                lottoProperties.getPurchaseResultTimeoutMs(),
                lottoProperties.getPurchaseResultPollIntervalMs());

        long deadlineMs = System.currentTimeMillis() + lottoProperties.getPurchaseResultTimeoutMs();
        int attempt = 1;
        do {
            navigateResultTable(page);
            List<ResultRow> resultRows = loadResultRows(page);
            Optional<ResultRow> selectedRow = selectNewPurchaseRow(resultRows, purchaseReceipt);
            if (selectedRow.isPresent()) {
                CheckResult checkResult = captureDetail(page, selectedRow.get());
                log.info("[PurchaseCheck] Complete attempt={} round={}",
                        attempt, checkResult.result().round());
                return checkResult;
            }

            log.info("[PurchaseCheck] Fresh purchase result not found attempt={} resultCount={}",
                    attempt, resultRows.size());
            waitBeforeNextPoll(page, deadlineMs);
            attempt++;
        } while (System.currentTimeMillis() < deadlineMs);

        throw new IllegalStateException("Fresh purchase result was not found in lotto ledger within "
                + lottoProperties.getPurchaseResultTimeoutMs() + "ms");
    }

    private Optional<ResultRow> selectNewPurchaseRow(
            List<ResultRow> resultRows,
            PurchaseReceipt purchaseReceipt
    ) {
        Optional<LottoResult> selectedResult = LottoPurchaseResultSelector.selectNewPurchase(
                resultRows.stream().map(ResultRow::result).toList(),
                purchaseReceipt
        );

        return selectedResult.flatMap(result -> resultRows.stream()
                .filter(row -> LottoPurchaseResultSelector.key(row.result())
                        .equals(LottoPurchaseResultSelector.key(result)))
                .findFirst());
    }

    private void navigateResultTable(Page page) {
        page.navigate(LottoBrowserConstants.RESULT_TABLE);
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    private List<ResultRow> loadResultRows(Page page) {
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
                String rowText = row.innerText();
                if (rowText.contains("조회 결과가 없습니다")) {
                    continue;
                }

                resultRows.add(new ResultRow(row, extractResult(row)));
            } catch (Exception exception) {
                log.warn("[Check] Skip invalid row index={}", index, exception);
            }
        }
        return resultRows;
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

    private void waitBeforeNextPoll(Page page, long deadlineMs) {
        long remainingMs = deadlineMs - System.currentTimeMillis();
        if (remainingMs <= 0) {
            return;
        }

        page.waitForTimeout(Math.min(lottoProperties.getPurchaseResultPollIntervalMs(), remainingMs));
    }

    private record ResultRow(Locator row, LottoResult result) {
    }
}
