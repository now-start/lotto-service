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
import org.nowstart.lotto.application.port.out.SendNotificationPort.Attachment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PlaywrightTraceArchive {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final String TRACE_GLOB = "lotto-trace-*.zip";
    /** 이보다 큰 트레이스는 메모리로 읽지 않는다(메일 첨부 상한은 메일 어댑터가 따로 적용). */
    private static final long MAX_TRACE_READ_BYTES = 20L * 1024 * 1024;
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

    /**
     * DOM 스냅샷만 기록한다. 스크린캐스트(screenshots)와 소스(sources)는 용량만 키워 메일 첨부가 거부됐다(552).
     * 호출자는 로그인 이후에 시작해야 한다 — 그 전에 시작하면 비밀번호 입력값이 트레이스에 평문으로 남는다.
     */
    public void start(BrowserContext context) {
        context.tracing().start(new Tracing.StartOptions()
                .setScreenshots(false)
                .setSnapshots(true)
                .setSources(false));
    }

    public Attachment stop(BrowserContext context, boolean saveTrace) {
        try {
            if (!saveTrace) {
                context.tracing().stop();
                return null;
            }
            synchronized (cleanupMonitor) {
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
                String traceId = UUID.randomUUID().toString().substring(0, 8);
                Path tracePath = Paths.get(logPath, "lotto-trace-" + timestamp + "-" + traceId + ".zip");
                context.tracing().stop(new Tracing.StopOptions().setPath(tracePath));
                long traceBytes = Files.size(tracePath);
                if (traceBytes > MAX_TRACE_READ_BYTES) {
                    log.warn("[Trace] {} is {} bytes - not attached, kept on server", tracePath, traceBytes);
                    return null;
                }
                Attachment attachment = new Attachment(tracePath.getFileName().toString(), Files.readAllBytes(tracePath),
                        Attachment.ZIP);
                try {
                    cleanupOldTraceFiles();
                } catch (IOException exception) {
                    log.warn("트레이스 정리 중 오류 발생", exception);
                }
                return attachment;
            }
        } catch (Exception exception) {
            log.warn("트레이스 종료 중 오류 발생", exception);
            return null;
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
