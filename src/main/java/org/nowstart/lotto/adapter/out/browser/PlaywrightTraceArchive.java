package org.nowstart.lotto.adapter.out.browser;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Tracing;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.config.LottoProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlaywrightTraceArchive {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final String TRACE_GLOB = "lotto-trace-*.zip";
    private final Object cleanupMonitor = new Object();

    // 트레이스는 로그인 비밀번호 입력·계정/예치금 등 민감 정보를 캡처하므로 기본 비활성화한다.
    private final LottoProperties lottoProperties;

    @Value("${logging.file.path:./logs}")
    private String logPath;

    @Value("${logging.logback.rollingpolicy.max-history:7}")
    private int maxTraceFiles;

    @PostConstruct
    void initialize() {
        try {
            ensureLogDirectoryExists();
            cleanupOldTraceFiles();
        } catch (IOException exception) {
            log.warn("초기화 중 오류 발생", exception);
        }
    }

    public void start(BrowserContext context) {
        if (!isTraceEnabled()) {
            return;
        }
        context.tracing().start(new Tracing.StartOptions()
                .setScreenshots(true)
                .setSnapshots(true)
                .setSources(true));
    }

    public void stop(BrowserContext context) {
        if (!isTraceEnabled()) {
            return;
        }
        try {
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
            String traceId = UUID.randomUUID().toString().substring(0, 8);
            Path tracePath = Paths.get(logPath, "lotto-trace-" + timestamp + "-" + traceId + ".zip");
            context.tracing().stop(new Tracing.StopOptions().setPath(tracePath));
            cleanupOldTraceFiles();
        } catch (Exception exception) {
            log.warn("페이지 종료 중 오류 발생", exception);
        }
    }

    private boolean isTraceEnabled() {
        return Boolean.TRUE.equals(lottoProperties.getTraceEnabled());
    }

    private void ensureLogDirectoryExists() throws IOException {
        Files.createDirectories(Paths.get(logPath));
    }

    private void cleanupOldTraceFiles() throws IOException {
        synchronized (cleanupMonitor) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(logPath), TRACE_GLOB)) {
                StreamSupport.stream(stream.spliterator(), false)
                        .sorted((first, second) -> {
                            try {
                                return Files.getLastModifiedTime(second).compareTo(Files.getLastModifiedTime(first));
                            } catch (IOException exception) {
                                return 0;
                            }
                        })
                        .skip(maxTraceFiles)
                        .forEach(file -> {
                            try {
                                Files.deleteIfExists(file);
                            } catch (IOException exception) {
                                log.warn("오래된 trace 파일 삭제 실패 path={}", file, exception);
                            }
                        });
            }
        }
    }
}
