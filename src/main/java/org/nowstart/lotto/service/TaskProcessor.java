package org.nowstart.lotto.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Tracing;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.data.dto.PageDto;
import org.nowstart.lotto.data.dto.ResultDto;
import org.nowstart.lotto.data.dto.TaskResult;
import org.nowstart.lotto.data.dto.UserDto;
import org.nowstart.lotto.data.entity.UserEntity;
import org.nowstart.lotto.data.type.ConstantsType;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@StepScope
@RequiredArgsConstructor
public class TaskProcessor implements ItemProcessor<UserEntity, TaskResult>, StepExecutionListener {

    public static final ThreadLocal<UserEntity> CURRENT_USER = new ThreadLocal<>();
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final Browser browser;

    @Value("#{jobParameters['buyLotto']}")
    private boolean buyLotto;

    @Value("#{jobParameters['failureSubject']}")
    private String failureSubject;

    @Value("${logging.file.path:./logs}")
    private String logPath;

    @Value("${logging.logback.rollingpolicy.max-history:7}")
    private int maxTraceFiles;

    @PostConstruct
    private void initialize() {
        try {
            Files.createDirectories(Paths.get(logPath));
            cleanupOldTraceFiles();
        } catch (IOException e) {
            log.warn("초기화 중 오류 발생", e);
        }
    }

    @Override
    public TaskResult process(UserEntity user) {
        log.info("[Processor][{}] - Start, buy: {}", user.getId(), buyLotto);
        CURRENT_USER.set(user);

        try (PageDto pageDto = createManagedPage()) {
            Page page = pageDto.page();

            login(page, user);

            if (buyLotto) {
                buy(page, user);
            }

            List<ResultDto> results = check(page);

            return TaskResult.builder()
                    .user(user)
                    .success(true)
                    .results(results)
                    .failureSubject(failureSubject)
                    .build();
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        CURRENT_USER.remove();
        return null;
    }

    // Page Management Logic
    private PageDto createManagedPage() {
        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent(ConstantsType.USER_AGENT_CHROME.getValue())
                .setIsMobile(false));

        context.addInitScript(ConstantsType.SCRIPT_CHROME_PLATFORM.getValue());
        context.tracing().start(new Tracing.StartOptions()
                .setScreenshots(true)
                .setSnapshots(true)
                .setSources(true));

        return new PageDto(context.newPage(), this::closePage);
    }

    private void closePage(Page page) {
        if (page == null || page.isClosed()) {
            return;
        }

        BrowserContext context = page.context();
        try {
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
            Path tracePath = Paths.get(logPath, String.format("trace-%s.zip", timestamp));
            context.tracing().stop(new Tracing.StopOptions().setPath(tracePath));
            cleanupOldTraceFiles();
        } catch (Exception e) {
            log.warn("페이지 종료 중 오류 발생", e);
        } finally {
            try {
                if (!page.isClosed()) {
                    page.close();
                }
                context.close();
            } catch (Exception e) {
                log.warn("리소스 종료 실패", e);
            }
        }
    }

    private void cleanupOldTraceFiles() throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(logPath), "*.zip")) {
            StreamSupport.stream(stream.spliterator(), false)
                    .sorted((a, b) -> {
                        try {
                            return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .skip(maxTraceFiles)
                    .forEach(file -> {
                        try {
                            Files.delete(file);
                        } catch (IOException e) {
                            log.warn("파일 삭제 실패: {}", file, e);
                        }
                    });
        }
    }

    // Business Logic
    private UserDto login(Page page, UserEntity user) {
        log.info("[Login][{}] - Start", user.getId());
        page.navigate(ConstantsType.URL_LOGIN.getValue());

        Locator idInput = page.getByPlaceholder(ConstantsType.ID_INPUT.getValue());
        if (idInput.isVisible()) {
            idInput.fill(user.getId());
            page.getByPlaceholder(ConstantsType.PASSWORD_INPUT.getValue()).fill(user.getPassword());
            page.click(ConstantsType.LOGIN_LINK.getValue());
        }

        Locator changeLaterLink = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions()
                .setName(ConstantsType.CHANGE_LATER.getValue()));
        if (changeLaterLink.isVisible()) {
            changeLaterLink.click();
        }
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.navigate(ConstantsType.URL_MY_PAGE.getValue());

        UserDto userDto = UserDto.builder()
                .name(page.locator(ConstantsType.USER_NAME.getValue()).innerText())
                .deposit(page.locator(ConstantsType.USER_DEPOSIT.getValue()).innerText())
                .build();

        log.info("[Login][{}] - Success, deposit: {}", user.getId(), userDto.getDeposit());
        return userDto;
    }

    private List<ResultDto> check(Page page) {
        log.info("[Check] Start");
        page.navigate(ConstantsType.RESULT_TABLE.getValue());
        page.click(ConstantsType.DETAIL_BTN.getValue());
        page.click(ConstantsType.DATE_RANGE_THIRD_BTN.getValue());
        page.click(ConstantsType.SEARCH_BUTTON.getValue());
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator rows = page.locator(ConstantsType.RESULT_ROW.getValue());
        rows.first().waitFor();
        int count = rows.count();
        log.info("[Check] Found {} rows", count);

        return IntStream.range(0, count)
                .mapToObj(rows::nth)
                .filter(r -> {
                    String text = r.innerText();
                    boolean hasNoResult = text.contains("조회 결과가 없습니다");
                    log.info("[Check] Row text: {}, hasNoResult: {}", text.replace("\n", " "), hasNoResult);
                    return !hasNoResult;
                })
                .map(r -> {
                    Locator numberLocator = r.locator(ConstantsType.RESULT_COL_NUMBER.getValue());
                    numberLocator.click();
                    Locator modalLocator = page.locator(ConstantsType.DETAIL_MODAL.getValue());
                    modalLocator.waitFor();
                    ByteArrayResource image = new ByteArrayResource(modalLocator.screenshot());
                    page.click(ConstantsType.CLOSE_DETAIL_BTN.getValue());

                    return ResultDto.builder()
                            .date(r.locator(ConstantsType.RESULT_COL_DATE1.getValue()).innerText().trim())
                            .round(r.locator(ConstantsType.RESULT_COL_ROUND.getValue()).innerText().trim())
                            .name(r.locator(ConstantsType.RESULT_COL_NAME.getValue()).innerText().trim())
                            .number(numberLocator.innerText().trim().replace(" ", ""))
                            .count(r.locator(ConstantsType.RESULT_COL_COUNT.getValue()).innerText().trim())
                            .result(r.locator(ConstantsType.RESULT_COL_RESULT.getValue()).innerText().trim())
                            .price(r.locator(ConstantsType.RESULT_COL_PRICE.getValue()).innerText().trim())
                            .image(image)
                            .build();
                }).toList();
    }

    private void buy(Page page, UserEntity user) {
        log.info("[Purchase][{}] - Proceeding, count: {}", user.getId(), user.getCount());
        page.navigate(ConstantsType.URL_PURCHASE.getValue());
        page.waitForLoadState(LoadState.NETWORKIDLE);

        page.locator(ConstantsType.AUTO_NUMBER.getValue()).click();
        page.locator(ConstantsType.QUANTITY_BOX.getValue()).selectOption(String.valueOf(user.getCount()));
        page.locator(ConstantsType.CONFIRM_BTN.getValue()).click();
        page.locator(ConstantsType.PURCHASE_BTN.getValue()).click();
        page.locator(ConstantsType.FINAL_CONFIRM_BTN.getValue()).click();
    }


}
