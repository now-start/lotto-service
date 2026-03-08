package org.nowstart.lotto.service;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.data.dto.LottoResultDto;
import org.nowstart.lotto.data.type.LottoConstantsType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LottoResultService {

    @Retryable(
            includes = Exception.class,
            maxRetriesString = "${lotto.max-retries:3}",
            delayString = "${lotto.retry-delay-ms:2000}"
    )
    public List<LottoResultDto> check(Page page) {
        log.info("[Check] Start");
        page.navigate(LottoConstantsType.RESULT_TABLE.getValue());
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator rows = page.locator(LottoConstantsType.RESULT_ROW.getValue());
        int rowCount = rows.count();
        if (rowCount == 0) {
            log.info("[Check] No rows found");
            return List.of();
        }

        List<LottoResultDto> results = new ArrayList<>();
        for (int index = 0; index < rowCount; index++) {
            try {
                Locator row = rows.nth(index);
                String rowText = row.innerText();
                if (rowText.contains("조회 결과가 없습니다")) {
                    continue;
                }

                results.add(extractResult(page, row));
            } catch (Exception e) {
                log.warn("[Check] Skip invalid row index={}", index, e);
            }
        }

        log.info("[Check] Complete resultCount={}", results.size());
        return results;
    }

    private LottoResultDto extractResult(Page page, Locator row) {
        Locator numberLocator = row.locator(LottoConstantsType.RESULT_COL_NUMBER.getValue());
        numberLocator.click();

        Locator modalLocator = page.locator(LottoConstantsType.DETAIL_MODAL.getValue());
        modalLocator.waitFor();
        ByteArrayResource image = new ByteArrayResource(modalLocator.screenshot());
        page.click(LottoConstantsType.CLOSE_DETAIL_BTN.getValue());

        return LottoResultDto.builder()
                .date(row.locator(LottoConstantsType.RESULT_COL_DATE1.getValue()).innerText().trim())
                .round(row.locator(LottoConstantsType.RESULT_COL_ROUND.getValue()).innerText().trim())
                .name(row.locator(LottoConstantsType.RESULT_COL_NAME.getValue()).innerText().trim())
                .number(numberLocator.innerText().trim().replace(" ", ""))
                .count(row.locator(LottoConstantsType.RESULT_COL_COUNT.getValue()).innerText().trim())
                .result(row.locator(LottoConstantsType.RESULT_COL_RESULT.getValue()).innerText().trim())
                .price(row.locator(LottoConstantsType.RESULT_COL_PRICE.getValue()).innerText().trim())
                .lottoImage(image)
                .build();
    }
}
