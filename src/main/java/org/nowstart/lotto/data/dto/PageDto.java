package org.nowstart.lotto.data.dto;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Tracing;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.data.exception.LottoServiceException;
import org.nowstart.lotto.data.type.LottoConstantsType;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Data
public class PageDto implements AutoCloseable {

    private static final String TRACE_DIR = "./logs/traces";
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final int TRACE_RETENTION_DAYS = 7;

    private final BrowserContext context;
    private final Page page;
    private final String traceFileName;
    private volatile boolean closed = false;

    public PageDto(Browser browser) {
        this.traceFileName = generateTraceFileName();

        try {
            initializeTraceEnvironment();
            this.context = createBrowserContext(browser);
            this.page = context.newPage();

            log.debug("브라우저 페이지 컨텍스트 생성 완료: {}", traceFileName);

        } catch (Exception e) {
            log.error("브라우저 페이지 초기화 실패", e);
            throw new LottoServiceException.PageInitializationException("페이지 초기화 중 오류 발생", e);
        }
    }

    @Override
    public void close() {
        if (closed) {
            log.debug("이미 닫힌 페이지 컨텍스트입니다: {}", traceFileName);
            return;
        }

        try {
            log.debug("페이지 컨텍스트 정리 시작: {}", traceFileName);

            saveTrace();
            closeResources();

            closed = true;
            log.debug("페이지 컨텍스트 정리 완료: {}", traceFileName);

        } catch (Exception e) {
            log.warn("페이지 컨텍스트 정리 중 오류 발생: {}", e.getMessage());
        }
    }

    private void initializeTraceEnvironment() {
        ensureTraceDirectoryExists();
        cleanupOldTraceFiles();
    }

    private BrowserContext createBrowserContext(Browser browser) {
        BrowserContext browserContext = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent(LottoConstantsType.USER_AGENT_CHROME.getValue())
                .setIsMobile(false));

        browserContext.addInitScript(LottoConstantsType.SCRIPT_CHROME_PLATFORM.getValue());
        browserContext.tracing().start(new Tracing.StartOptions()
                .setScreenshots(true)
                .setSnapshots(true)
                .setSources(true));

        return browserContext;
    }

    private void saveTrace() {
        try {
            Path tracePath = Paths.get(TRACE_DIR, traceFileName);
            context.tracing().stop(new Tracing.StopOptions().setPath(tracePath));
            log.debug("트레이스 파일 저장 완료: {}", tracePath);
        } catch (Exception e) {
            log.warn("트레이스 저장 실패: {}", e.getMessage());
        }
    }

    private void closeResources() {
        if (page != null && !page.isClosed()) {
            page.close();
        }
        if (context != null) {
            context.close();
        }
    }

    private void ensureTraceDirectoryExists() {
        try {
            Path traceDir = Paths.get(TRACE_DIR);
            if (!Files.exists(traceDir)) {
                Files.createDirectories(traceDir);
                log.debug("트레이스 디렉터리 생성: {}", traceDir);
            }
        } catch (Exception e) {
            log.warn("트레이스 디렉터리 생성 실패: {}", e.getMessage());
        }
    }

    private void cleanupOldTraceFiles() {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(TRACE_DIR), "*.zip")) {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(TRACE_RETENTION_DAYS);

            for (Path file : stream) {
                deleteTraceFileIfOld(file, cutoffDate);
            }
        } catch (IOException e) {
            log.warn("트레이스 파일 정리 중 오류: {}", e.getMessage());
        }
    }

    private void deleteTraceFileIfOld(Path file, LocalDateTime cutoffDate) {
        try {
            LocalDateTime fileTime = Files.getLastModifiedTime(file).toInstant()
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();

            if (fileTime.isBefore(cutoffDate)) {
                Files.delete(file);
                log.debug("오래된 트레이스 파일 삭제: {}", file);
            }
        } catch (Exception e) {
            log.warn("트레이스 파일 삭제 실패: {}", file, e);
        }
    }

    private String generateTraceFileName() {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        return String.format("lotto-trace-%s.zip", timestamp);
    }
}
