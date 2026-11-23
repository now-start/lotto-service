package org.nowstart.lotto.adapter.outbound.browser

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import org.nowstart.lotto.application.port.outbound.Attachment
import org.nowstart.lotto.application.port.outbound.LottoAutomationSession
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

private val log = LoggerFactory.getLogger(PlaywrightSessionManager::class.java)

@Component
class PlaywrightSessionManager(
    private val browserLaunchOptions: Supplier<BrowserType.LaunchOptions>,
    private val traceArchive: PlaywrightTraceArchive,
    private val settings: BrowserSettings,
) {

    private val sessionLimiter = Semaphore(settings.maxConcurrentSessions, true)

    fun openSession(): LottoAutomationSession {
        acquirePermit()
        val permitReleased = AtomicBoolean(false)
        val releasePermit = {
            if (permitReleased.compareAndSet(false, true)) {
                sessionLimiter.release()
            }
        }

        var playwright: Playwright? = null
        var browser: Browser? = null
        var context: BrowserContext? = null
        try {
            val createdPlaywright = Playwright.create().also { playwright = it }
            val createdBrowser = createdPlaywright.chromium().launch(browserLaunchOptions.get()).also { browser = it }
            val createdContext = createdBrowser.newContext(
                Browser.NewContextOptions()
                    .setUserAgent(LottoBrowserConstants.USER_AGENT_CHROME)
                    .setIsMobile(false),
            ).also { context = it }
            createdContext.addInitScript(LottoBrowserConstants.SCRIPT_CHROME_PLATFORM)
            // 트레이스는 로그인 이후 startTracing()에서 시작한다(비밀번호 입력값이 트레이스에 남지 않도록).
            return PlaywrightLottoSession(createdContext.newPage(), createdContext, createdBrowser, createdPlaywright, traceArchive, releasePermit)
        } catch (failure: Throwable) {
            closeQuietly(context, "브라우저 컨텍스트 종료 실패")
            closeQuietly(browser, "브라우저 종료 실패")
            closeQuietly(playwright, "Playwright 종료 실패")
            releasePermit()
            throw failure
        }
    }

    /** 로그인 이후에 트레이스를 시작한다. 트레이스는 진단용이므로 시작에 실패해도 작업은 계속한다. */
    fun startTracing(session: LottoAutomationSession) {
        val playwrightSession = session as? PlaywrightLottoSession ?: return
        if (!playwrightSession.traceStarted.compareAndSet(false, true)) {
            return
        }
        try {
            traceArchive.start(playwrightSession.context)
        } catch (exception: RuntimeException) {
            playwrightSession.traceStarted.set(false)
            log.warn("트레이스 시작 실패 - 트레이스 없이 계속 진행", exception)
        }
    }

    fun requirePage(session: LottoAutomationSession): Page =
        (session as? PlaywrightLottoSession)?.page
            ?: throw IllegalArgumentException("Unsupported session type: ${session.javaClass.name}")

    /**
     * 세마포어 대기를 무한정 하지 않고 제한한다. 모든 permit이 hung 세션에 잡혀 있어
     * 확보하지 못하면 예외로 종료해, future가 반드시 완료되고 in-flight 키가 해제되도록 한다(영구 skip 방지).
     */
    private fun acquirePermit() {
        val timeoutMs = SESSION_PERMIT_TIMEOUT_MS
        try {
            check(sessionLimiter.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                "브라우저 세션 허가를 ${timeoutMs}ms 내에 확보하지 못했습니다 (동시 세션 한도 초과/지연)"
            }
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("브라우저 세션 허가 획득 중 인터럽트되었습니다", exception)
        }
    }

    private class PlaywrightLottoSession(
        val page: Page,
        val context: BrowserContext,
        private val browser: Browser,
        private val playwright: Playwright,
        private val traceArchive: PlaywrightTraceArchive,
        private val releasePermit: () -> Unit,
    ) : LottoAutomationSession {

        private val failed = AtomicBoolean(false)
        val traceStarted = AtomicBoolean(false)
        private val trace = AtomicReference<Attachment?>()

        override fun markFailed() {
            failed.set(true)
        }

        override fun failureTrace(): Attachment? = trace.get()

        override fun close() {
            try {
                trace.set(captureFailureArtifact())
            } finally {
                try {
                    closePage()
                    closeQuietly(context, "브라우저 컨텍스트 종료 실패")
                    closeQuietly(browser, "브라우저 종료 실패")
                    closeQuietly(playwright, "Playwright 종료 실패")
                } finally {
                    releasePermit()
                }
            }
        }

        /** 실패한 세션의 진단 자료: 로그인 후면 트레이스, 로그인 전이면 화면 캡처(트레이스는 시작 전). */
        private fun captureFailureArtifact(): Attachment? {
            if (traceStarted.get()) {
                return traceArchive.stop(context, failed.get())
            }
            if (!failed.get() || page.isClosed) {
                return null
            }
            return try {
                val png = page.screenshot(Page.ScreenshotOptions().setFullPage(true))
                Attachment("lotto-failure-${System.currentTimeMillis()}.png", png, Attachment.PNG)
            } catch (exception: RuntimeException) {
                log.warn("실패 화면 캡처 실패", exception)
                null
            }
        }

        private fun closePage() {
            try {
                if (!page.isClosed) {
                    page.close()
                }
            } catch (exception: Exception) {
                log.warn("페이지 종료 실패", exception)
            }
        }
    }
}

/** 브라우저 슬롯을 기다리는 최대 시간. 넘기면 이번 시도는 실패하고 작업 단위로 다시 시도한다. */
private const val SESSION_PERMIT_TIMEOUT_MS = 10L * 60 * 1000

private fun closeQuietly(closeable: AutoCloseable?, failureMessage: String) {
    try {
        closeable?.close()
    } catch (exception: Exception) {
        log.warn(failureMessage, exception)
    }
}
