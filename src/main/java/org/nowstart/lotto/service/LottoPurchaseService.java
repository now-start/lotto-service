package org.nowstart.lotto.service;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.data.properties.LottoProperties;
import org.nowstart.lotto.data.type.LottoConstantsType;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LottoPurchaseService {

    @Retryable(
            includes = Exception.class,
            maxRetriesString = "${lotto.max-retries:3}",
            delayString = "${lotto.retry-delay-ms:2000}"
    )
    public void buy(Page page, LottoProperties.User user) {
        log.info("[Purchase][{}] Start count={}", user.getId(), user.getCount());

        page.navigate(LottoConstantsType.URL_PURCHASE.getValue());
        page.waitForLoadState(LoadState.NETWORKIDLE);

        page.locator(LottoConstantsType.AUTO_NUMBER.getValue()).click();
        page.locator(LottoConstantsType.QUANTITY_BOX.getValue()).selectOption(String.valueOf(user.getCount()));
        page.locator(LottoConstantsType.CONFIRM_BTN.getValue()).click();
        page.locator(LottoConstantsType.PURCHASE_BTN.getValue()).click();
        page.locator(LottoConstantsType.FINAL_CONFIRM_BTN.getValue()).click();

        log.info("[Purchase][{}] Success", user.getId());
    }
}
