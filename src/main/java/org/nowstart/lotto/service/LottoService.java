package org.nowstart.lotto.service;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.data.dto.LottoResultDto;
import org.nowstart.lotto.data.dto.LottoUserDto;
import org.nowstart.lotto.data.dto.PageDto;
import org.nowstart.lotto.data.exception.LottoServiceException;
import org.nowstart.lotto.data.properties.LottoProperties;
import org.nowstart.lotto.data.type.LottoConstantsType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LottoService {

    private final LottoProperties lottoProperties;

    /**
     * 로또 로그인
     */
    public LottoUserDto loginLotto(PageDto pageDto) {
        try {
            log.info("로또 사이트 로그인 시작");
            Page page = pageDto.getPage();

            page.navigate(LottoConstantsType.URL_LOGIN.getValue());

            Locator idInput = page.getByPlaceholder(LottoConstantsType.ID_INPUT.getValue());
            if (idInput.isVisible()) {
                idInput.fill(lottoProperties.getId());
                page.getByPlaceholder(LottoConstantsType.PASSWORD_INPUT.getValue()).fill(lottoProperties.getPassword());
                page.getByRole(AriaRole.GROUP, new Page.GetByRoleOptions().setName(LottoConstantsType.LOGIN_GROUP.getValue()))
                        .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(LottoConstantsType.LOGIN_LINK.getValue()))
                        .click();
            }

            handlePasswordChangeDialog(page);
            return extractUserInfo(page);
        } catch (Exception e) {
            log.error("로그인 자동화 실패", e);
            throw new LottoServiceException.WebAutomationException("로그인 자동화 실패", e);
        }
    }

    /**
     * 로또 당첨 확인
     */
    public List<LottoResultDto> checkLotto(PageDto pageDto) {
        try {
            log.info("로또 결과 확인 시작");
            Page page = pageDto.getPage();

            page.navigate(LottoConstantsType.URL_MY_PAGE.getValue());
            Locator table = page.locator(LottoConstantsType.RESULT_TABLE.getValue());

            return table.all().stream().map(row -> LottoResultDto.builder()
                    .date(row.locator("td:nth-child(1)").innerText())
                    .round(row.locator("td:nth-child(2)").innerText())
                    .name(row.locator("td:nth-child(3)").innerText())
                    .number(row.locator("td:nth-child(4)").innerText().replace(" ", ""))
                    .count(row.locator("td:nth-child(5)").innerText())
                    .result(row.locator("td:nth-child(6)").innerText())
                    .price(row.locator("td:nth-child(7)").innerText())
                    .build()).toList();
        } catch (Exception e) {
            log.error("로또 결과 추출 실패", e);
            throw new LottoServiceException.WebAutomationException("로또 결과 추출 실패", e);
        }
    }

    /**
     * 로또 상세 확인
     */
    public ByteArrayResource detailLotto(PageDto pageDto, LottoResultDto lottoResultDto) {
        try {
            log.info("로또 상세 정보 스크린샷 촬영: {}", lottoResultDto.getNumber());
            Page page = pageDto.getPage();

            String detailUrl = LottoConstantsType.URL_DETAIL_BASE.getFormattedValue(lottoResultDto.getNumber());
            page.navigate(detailUrl);

            return new ByteArrayResource(page.screenshot());
        } catch (Exception e) {
            log.error("로또 상세 스크린샷 촬영 실패: {}", lottoResultDto.getNumber(), e);
            throw new LottoServiceException.WebAutomationException("스크린샷 촬영 실패", e);
        }
    }

    /**
     * 로또 구매
     */
    public void buyLotto(PageDto pageDto) {
        try {
            log.info("로또 구매 진행: {} 장", lottoProperties.getCount());
            Page page = pageDto.getPage();

            page.navigate(LottoConstantsType.URL_PURCHASE.getValue());
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions()
                    .setName(LottoConstantsType.AUTO_NUMBER.getValue())).click();
            page.getByRole(AriaRole.COMBOBOX, new Page.GetByRoleOptions()
                    .setName(LottoConstantsType.QUANTITY_BOX.getValue())).selectOption(String.valueOf(lottoProperties.getCount()));

            Locator confirmButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                    .setName(LottoConstantsType.CONFIRM_BTN.getValue()));
            confirmButton.click();
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                    .setName(LottoConstantsType.PURCHASE_BTN.getValue())).click();
            confirmButton.nth(1).click();
        } catch (Exception e) {
            log.error("로또 구매 자동화 실패: {} 장", lottoProperties.getCount(), e);
            throw new LottoServiceException.WebAutomationException("로또 구매 자동화 실패", e);
        }
    }

    private void handlePasswordChangeDialog(Page page) {
        Locator changeLaterLink = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions()
                .setName(LottoConstantsType.CHANGE_LATER.getValue()));
        if (changeLaterLink.isVisible()) {
            changeLaterLink.click();
        }
    }

    private LottoUserDto extractUserInfo(Page page) {
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.navigate(LottoConstantsType.URL_MAIN.getValue());

        Locator information = page.locator(LottoConstantsType.USER_INFO.getValue());

        return LottoUserDto.builder()
                .name(information.locator(LottoConstantsType.USER_NAME.getValue()).innerText())
                .deposit(information.locator(LottoConstantsType.USER_DEPOSIT.getValue()).innerText())
                .build();
    }
}