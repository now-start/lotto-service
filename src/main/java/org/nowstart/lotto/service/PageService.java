package org.nowstart.lotto.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Tracing;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.data.dto.PageDto;
import org.nowstart.lotto.data.type.LottoConstantsType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.StreamSupport;

@Slf4j
@Service
@RequiredArgsConstructor
public class PageService {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final Browser browser;

    @Value("${logging.file.path:./logs}")
    private String logPath;

    @Value("${logging.logback.rollingpolicy.max-history:7}")
    private int maxTraceFiles;

    @PostConstruct
    private void initialize() {
        try {
            ensureLogDirectoryExists();
            cleanupOldTraceFiles();
        } catch (IOException e) {
            log.warn("초기화 중 오류 발생", e);
        }
    }

    public PageDto createManagedPage() {
        return new PageDto(createPage(), this);
    }

    public Page createPage() {
        BrowserContext context = createBrowserContext();
        return context.newPage();
    }

    private BrowserContext createBrowserContext() {
        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent(LottoConstantsType.USER_AGENT_CHROME.getValue())
                .setIsMobile(false));

        context.addInitScript(LottoConstantsType.SCRIPT_CHROME_PLATFORM.getValue());
        context.tracing().start(new Tracing.StartOptions()
                .setScreenshots(true)
                .setSnapshots(true)
                .setSources(true));

        return context;
    }

    public void closePage(Page page) {
        if (page == null || page.isClosed()) {
            return;
        }

        BrowserContext context = page.context();
        try {
            saveTrace(context);
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

    private void saveTrace(BrowserContext context) throws IOException {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        Path tracePath = Paths.get(logPath, String.format("lotto-trace-%s.zip", timestamp));
        context.tracing().stop(new Tracing.StopOptions().setPath(tracePath));
        cleanupOldTraceFiles();
    }

    private void ensureLogDirectoryExists() throws IOException {
        Path logDirPath = Paths.get(logPath);
        Files.createDirectories(logDirPath);
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
                            throw new RuntimeException(e);
                        }
                    });
        }
    }
}