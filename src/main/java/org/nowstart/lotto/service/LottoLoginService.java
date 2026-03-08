package org.nowstart.lotto.service;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.data.dto.LottoUserDto;
import org.nowstart.lotto.data.properties.LottoProperties;
import org.nowstart.lotto.data.type.LottoConstantsType;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LottoLoginService {

    @Retryable(
            includes = Exception.class,
            maxRetriesString = "${lotto.max-retries:3}",
            delayString = "${lotto.retry-delay-ms:2000}"
    )
    public LottoUserDto login(Page page, LottoProperties.User user) {
        log.info("[Login][{}] Start", user.getId());
        page.navigate(LottoConstantsType.URL_LOGIN.getValue());

        Locator idInput = page.getByPlaceholder(LottoConstantsType.ID_INPUT.getValue());
        if (idInput.isVisible()) {
            idInput.fill(user.getId());
            page.getByPlaceholder(LottoConstantsType.PASSWORD_INPUT.getValue()).fill(user.getPassword());
            page.click(LottoConstantsType.LOGIN_LINK.getValue());
        }

        Locator changeLaterLink = page.getByRole(
                AriaRole.LINK,
                new Page.GetByRoleOptions().setName(LottoConstantsType.CHANGE_LATER.getValue())
        );
        if (changeLaterLink.isVisible()) {
            changeLaterLink.click();
        }

        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.navigate(LottoConstantsType.URL_MY_PAGE.getValue());

        LottoUserDto lottoUserDto = LottoUserDto.builder()
                .name(page.locator(LottoConstantsType.USER_NAME.getValue()).innerText())
                .deposit(page.locator(LottoConstantsType.USER_DEPOSIT.getValue()).innerText())
                .build();

        log.info("[Login][{}] Success deposit={}", user.getId(), lottoUserDto.getDeposit());
        return lottoUserDto;
    }
}
