package org.nowstart.lotto.adapter.outbound.browser

import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.Tracing
import jakarta.annotation.PostConstruct
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.nowstart.lotto.application.port.outbound.Attachment
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

private val log = LoggerFactory.getLogger(PlaywrightTraceArchive::class.java)

@Component
class PlaywrightTraceArchive(
    @Value("\${logging.file.path:./logs}") private val logPath: String,
    @Value("\${logging.logback.rollingpolicy.max-history:7}") private val maxTraceFiles: Int,
) {

    private val cleanupMonitor = Any()

    @PostConstruct
    fun initialize() {
        try {
            Files.createDirectories(Paths.get(logPath))
            cleanupOldTraceFiles()
        } catch (exception: IOException) {
            log.warn("초기화 중 오류 발생", exception)
        }
    }

    /**
     * DOM 스냅샷만 기록한다. 스크린캐스트(screenshots)와 소스(sources)는 용량만 키워 메일 첨부가 거부됐다(552).
     * 호출자는 로그인 이후에 시작해야 한다 — 그 전에 시작하면 비밀번호 입력값이 트레이스에 평문으로 남는다.
     */
    fun start(context: BrowserContext) {
        context.tracing().start(
            Tracing.StartOptions()
                .setScreenshots(false)
                .setSnapshots(true)
                .setSources(false),
        )
    }

    fun stop(context: BrowserContext, saveTrace: Boolean): Attachment? = try {
        if (!saveTrace) {
            context.tracing().stop()
            null
        } else {
            synchronized(cleanupMonitor) { saveTrace(context) }
        }
    } catch (exception: Exception) {
        log.warn("트레이스 종료 중 오류 발생", exception)
        null
    }

    private fun saveTrace(context: BrowserContext): Attachment? {
        val timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER)
        val traceId = UUID.randomUUID().toString().substring(0, 8)
        val tracePath = Paths.get(logPath, "lotto-trace-$timestamp-$traceId.zip")
        context.tracing().stop(Tracing.StopOptions().setPath(tracePath))
        val traceBytes = Files.size(tracePath)
        if (traceBytes > MAX_TRACE_READ_BYTES) {
            log.warn("[Trace] {} is {} bytes - not attached, kept on server", tracePath, traceBytes)
            return null
        }
        val attachment = Attachment(tracePath.fileName.toString(), Files.readAllBytes(tracePath), Attachment.ZIP)
        try {
            cleanupOldTraceFiles()
        } catch (exception: IOException) {
            log.warn("트레이스 정리 중 오류 발생", exception)
        }
        return attachment
    }

    private fun cleanupOldTraceFiles() {
        synchronized(cleanupMonitor) {
            Files.newDirectoryStream(Paths.get(logPath), TRACE_GLOB).use { stream ->
                stream.sortedByDescending { lastModified(it) }
                    .drop(maxTraceFiles)
                    .forEach { file ->
                        try {
                            Files.deleteIfExists(file)
                        } catch (exception: IOException) {
                            log.warn("오래된 trace 파일 삭제 실패 path={}", file, exception)
                        }
                    }
            }
        }
    }

    private fun lastModified(path: Path): Long = try {
        Files.getLastModifiedTime(path).toMillis()
    } catch (exception: IOException) {
        0L
    }

    private companion object {
        val TIMESTAMP_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
        const val TRACE_GLOB = "lotto-trace-*.zip"

        /** 이보다 큰 트레이스는 메모리로 읽지 않는다(메일 첨부 상한은 메일 어댑터가 따로 적용). */
        const val MAX_TRACE_READ_BYTES = 20L * 1024 * 1024
    }
}
