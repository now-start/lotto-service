package org.nowstart.lotto.adapter.out.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.CheckResult;
import org.nowstart.lotto.domain.model.LottoResult;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PlaywrightResultExecutor {

    @Retryable(
            includes = Exception.class,
            maxRetriesString = "${lotto.max-retries:3}",
            delayString = "${lotto.retry-delay-ms:2000}"
    )
    public List<CheckResult> check(Page page) {
        log.info("[Check] Start");
        page.navigate(LottoBrowserConstants.RESULT_TABLE);
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator rows = page.locator(LottoBrowserConstants.RESULT_ROW);
        int rowCount = rows.count();
        if (rowCount == 0) {
            log.info("[Check] No rows found");
            return List.of();
        }

        List<CheckResult> results = new ArrayList<>();
        for (int index = 0; index < rowCount; index++) {
            try {
                Locator row = rows.nth(index);
                String rowText = row.innerText();
                if (rowText.contains("조회 결과가 없습니다")) {
                    continue;
                }

                results.add(extractResult(page, row));
            } catch (Exception exception) {
                log.warn("[Check] Skip invalid row index={}", index, exception);
            }
        }

        log.info("[Check] Complete resultCount={}", results.size());
        return results;
    }

    private CheckResult extractResult(Page page, Locator row) {
        Locator numberLocator = row.locator(LottoBrowserConstants.RESULT_COL_NUMBER);
        numberLocator.click();

        Locator modalLocator = page.locator(LottoBrowserConstants.DETAIL_MODAL);
        modalLocator.waitFor();
        byte[] imageBytes = modalLocator.screenshot();
        page.click(LottoBrowserConstants.CLOSE_DETAIL_BTN);

        LottoResult result = new LottoResult(
                row.locator(LottoBrowserConstants.RESULT_COL_DATE1).innerText().trim(),
                row.locator(LottoBrowserConstants.RESULT_COL_ROUND).innerText().trim(),
                row.locator(LottoBrowserConstants.RESULT_COL_NAME).innerText().trim(),
                numberLocator.innerText().trim().replace(" ", ""),
                row.locator(LottoBrowserConstants.RESULT_COL_COUNT).innerText().trim(),
                row.locator(LottoBrowserConstants.RESULT_COL_RESULT).innerText().trim(),
                row.locator(LottoBrowserConstants.RESULT_COL_PRICE).innerText().trim()
        );

        return new CheckResult(result, imageBytes);
    }
}
