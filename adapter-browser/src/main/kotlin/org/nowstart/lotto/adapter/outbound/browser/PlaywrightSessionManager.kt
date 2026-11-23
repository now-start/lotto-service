package org.nowstart.lotto.adapter.outbound.browser

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import org.nowstart.lotto.application.port.outbound.Attachment
import org.nowstart.lotto.application.port.outbound.AutomationSession
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

private val log = LoggerFactory.getLogger(PlaywrightSessionManager::class.java)

@Component
class PlaywrightSessionManager(
    private val browserLaunchOptions: Supplier<BrowserType.LaunchOptions>,
    private val traceArchive: PlaywrightTraceArchive,
) {

    fun openSession(): AutomationSession {
        var playwright: Playwright? = null
        var browser: Browser? = null
        var context: BrowserContext? = null
        try {
            val createdPlaywright = Playwright.create().also { playwright = it }
            val createdBrowser = createdPlaywright.chromium().launch(browserLaunchOptions.get()).also { browser = it }
            val createdContext = createdBrowser.newContext(
                Browser.NewContextOptions()
                    .setUserAgent(BrowserConstants.USER_AGENT_CHROME)
                    .setIsMobile(false),
            ).also { context = it }
            createdContext.addInitScript(BrowserConstants.SCRIPT_CHROME_PLATFORM)
            // 트레이스는 로그인 이후 startTracing()에서 시작한다(비밀번호 입력값이 트레이스에 남지 않도록).
            return PlaywrightSession(createdContext.newPage(), createdContext, createdBrowser, createdPlaywright, traceArchive)
        } catch (failure: Throwable) {
            closeQuietly(context, "브라우저 컨텍스트 종료 실패")
            closeQuietly(browser, "브라우저 종료 실패")
            closeQuietly(playwright, "Playwright 종료 실패")
            throw failure
        }
    }

    /** 로그인 이후에 트레이스를 시작한다. 트레이스는 진단용이므로 시작에 실패해도 작업은 계속한다. */
    fun startTracing(session: AutomationSession) {
        val playwrightSession = session as? PlaywrightSession ?: return
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

    fun requirePage(session: AutomationSession): Page =
        (session as? PlaywrightSession)?.page
            ?: throw IllegalArgumentException("Unsupported session type: ${session.javaClass.name}")

    private class PlaywrightSession(
        val page: Page,
        val context: BrowserContext,
        private val browser: Browser,
        private val playwright: Playwright,
        private val traceArchive: PlaywrightTraceArchive,
    ) : AutomationSession {

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
                closePage()
                closeQuietly(context, "브라우저 컨텍스트 종료 실패")
                closeQuietly(browser, "브라우저 종료 실패")
                closeQuietly(playwright, "Playwright 종료 실패")
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

private fun closeQuietly(closeable: AutoCloseable?, failureMessage: String) {
    try {
        closeable?.close()
    } catch (exception: Exception) {
        log.warn(failureMessage, exception)
    }
}
