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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PlaywrightTraceArchive {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final String TRACE_GLOB = "lotto-trace-*.zip";
    private final Object cleanupMonitor = new Object();

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
        context.tracing().start(new Tracing.StartOptions()
                .setScreenshots(true)
                .setSnapshots(true)
                .setSources(true));
    }

    public void stop(BrowserContext context, boolean saveTrace) {
        try {
            if (!saveTrace) {
                context.tracing().stop();
                return;
            }
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
            String traceId = UUID.randomUUID().toString().substring(0, 8);
            Path tracePath = Paths.get(logPath, "lotto-trace-" + timestamp + "-" + traceId + ".zip");
            context.tracing().stop(new Tracing.StopOptions().setPath(tracePath));
            cleanupOldTraceFiles();
        } catch (Exception exception) {
            log.warn("트레이스 종료 중 오류 발생", exception);
        }
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
