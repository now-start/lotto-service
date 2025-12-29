package org.nowstart.lotto.service;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.data.dto.LottoResultDto;
import org.nowstart.lotto.data.dto.LottoUserDto;
import org.nowstart.lotto.data.dto.MessageDto;
import org.nowstart.lotto.data.dto.PageDto;
import org.nowstart.lotto.data.properties.LottoProperties;
import org.nowstart.lotto.data.type.LottoConstantsType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LottoService {


    private final PageService pageService;
    private final LottoProperties lottoProperties;
    private final GoogleNotifyService googleNotifyService;

    public LottoUserDto loginLotto(Page page, LottoProperties.User user) {
        log.info("[Login][{}] - Start", user.getId());

        page.navigate(LottoConstantsType.URL_LOGIN.getValue());

        Locator idInput = page.getByPlaceholder(LottoConstantsType.ID_INPUT.getValue());
        if (idInput.isVisible()) {
            idInput.fill(user.getId());
            page.getByPlaceholder(LottoConstantsType.PASSWORD_INPUT.getValue()).fill(user.getPassword());
            page.getByRole(AriaRole.GROUP, new Page.GetByRoleOptions().setName(LottoConstantsType.LOGIN_GROUP.getValue()))
                    .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(LottoConstantsType.LOGIN_LINK.getValue()))
                    .click();
        }

        Locator changeLaterLink = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions()
                .setName(LottoConstantsType.CHANGE_LATER.getValue()));
        if (changeLaterLink.isVisible()) {
            changeLaterLink.click();
        }
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.navigate(LottoConstantsType.URL_MAIN.getValue());
        Locator information = page.locator(LottoConstantsType.USER_INFO.getValue());

        LottoUserDto lottoUserDto = LottoUserDto.builder()
                .name(information.locator(LottoConstantsType.USER_NAME.getValue()).innerText())
                .deposit(information.locator(LottoConstantsType.USER_DEPOSIT.getValue()).innerText())
                .build();

        log.info("[Login][{}] - Success, deposit: {}", user.getId(), lottoUserDto.getDeposit());
        return lottoUserDto;
    }

    public void executeLottoTask(String failureSubject, boolean buyLotto) {
        log.info("[Task] Batch Start - buyLotto: {}, userCount: {}", buyLotto, lottoProperties.getUsers().size());

        for (LottoProperties.User user : lottoProperties.getUsers()) {
            executeLottoTaskForUser(user, failureSubject, buyLotto);
        }

        log.info("[Task] Batch Complete");
    }

    private void executeLottoTaskForUser(LottoProperties.User user, String failureSubject, boolean buyLotto) {
        try (PageDto pageDto = pageService.createManagedPage()) {
            Page page = pageDto.page();
            log.info("[Task][{}] - Start, buyLotto: {}", user.getId(), buyLotto);

            LottoUserDto lottoUserDto = executeWithRetry("Login", () -> loginLotto(page, user));

            if (buyLotto) {
                executeWithRetry("Purchase", () -> {
                    buyLotto(page, user);
                    log.info("[Purchase][{}] - Success", user.getId());
                    return null;
                });
            }

            log.info("[Check][{}] - Checking results", user.getId());
            List<LottoResultDto> results = checkLotto(page);

            if (!results.isEmpty()) {
                LottoResultDto latestResult = results.get(0);
                try {
                    googleNotifyService.send(MessageDto.builder()
                            .subject(String.format("[%s] %s", user.getId(), latestResult.toString()))
                            .text(lottoUserDto.toString())
                            .lottoImage(detailLotto(page, latestResult))
                            .to(user.getEmail())
                            .build());
                    log.info("[Notify][{}] - Success Notification Sent", user.getId());
                } catch (Exception e) {
                    log.error("[Notify][{}] - Success Notification Failed", user.getId(), e);
                }
            } else {
                log.info("[Check][{}] - No results found", user.getId());
            }

            log.info("[Task][{}] - Complete, deposit: {}", user.getId(), lottoUserDto.getDeposit());
        } catch (Exception e) {
            log.error("[Task][{}] - Failed, error: {}", user.getId(), e.getMessage(), e);
            try {
                String failureMessage = String.format("""
                                [사용자: %s]
                                작업 실행 중 오류가 발생했습니다.

                                오류 유형: %s
                                오류 메시지: %s

                                자세한 내용은 서버 로그를 확인해 주세요.""",
                        user.getId(), e.getClass().getSimpleName(), e.getMessage());

                googleNotifyService.send(MessageDto.builder()
                        .subject(String.format("[%s] %s", user.getId(), failureSubject))
                        .text(failureMessage)
                        .to(user.getEmail())
                        .build());
                log.info("[Notify][{}] - Failure Notification Sent", user.getId());
            } catch (Exception notificationError) {
                log.error("[Notify][{}] - Failure Notification Failed", user.getId(), notificationError);
            }
        }
    }

    private List<LottoResultDto> checkLotto(Page page) {
        log.info("[Check] Start");

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
    }

    private ByteArrayResource detailLotto(Page page, LottoResultDto lottoResultDto) {
        log.info("[Detail] Capturing screenshot - round: {}", lottoResultDto.getRound());

        String detailUrl = LottoConstantsType.URL_DETAIL_BASE.getFormattedValue(lottoResultDto.getNumber());
        page.navigate(detailUrl);

        return new ByteArrayResource(page.screenshot());
    }

    private void buyLotto(Page page, LottoProperties.User user) {
        log.info("[Purchase][{}] - Proceeding, count: {}", user.getId(), user.getCount());

        page.navigate(LottoConstantsType.URL_PURCHASE.getValue());
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions()
                .setName(LottoConstantsType.AUTO_NUMBER.getValue())).click();
        page.getByRole(AriaRole.COMBOBOX, new Page.GetByRoleOptions()
                .setName(LottoConstantsType.QUANTITY_BOX.getValue())).selectOption(String.valueOf(user.getCount()));

        Locator confirmButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(LottoConstantsType.CONFIRM_BTN.getValue()));
        confirmButton.click();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(LottoConstantsType.PURCHASE_BTN.getValue())).click();
        confirmButton.nth(1).click();
    }

    private <T> T executeWithRetry(String actionName, Supplier<T> action) {
        for (int attempt = 1; attempt <= lottoProperties.getMaxRetries(); attempt++) {
            try {
                log.info("[Retry] {} - Attempting ({}/{})", actionName, attempt, lottoProperties.getMaxRetries());
                return action.get();
            } catch (Exception e) {
                log.warn("[Retry] {} - Attempt {} Failed: {}", actionName, attempt, e.getMessage());

                if (attempt == lottoProperties.getMaxRetries()) {
                    log.error("[Retry] {} - Max retries ({}) exceeded", actionName, lottoProperties.getMaxRetries());
                    throw new RuntimeException(actionName + " 재시도 실패: " + e.getMessage(), e);
                }

                log.info("[Retry] {} - Waiting {}ms before retry", actionName, lottoProperties.getRetryDelayMs());
                try {
                    Thread.sleep(lottoProperties.getRetryDelayMs());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        throw new IllegalStateException("재시도 로직 오류");
    }
}