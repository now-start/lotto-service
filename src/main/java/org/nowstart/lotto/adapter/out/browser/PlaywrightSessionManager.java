package org.nowstart.lotto.adapter.out.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.nowstart.lotto.application.port.out.SendNotificationPort.Attachment;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.application.port.out.LottoAutomationSession;
import org.nowstart.lotto.config.LottoProperties;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PlaywrightSessionManager {

    private final Supplier<BrowserType.LaunchOptions> browserLaunchOptions;
    private final PlaywrightTraceArchive playWrightTraceArchive;
    private final LottoProperties lottoProperties;
    private final Semaphore sessionLimiter;

    public PlaywrightSessionManager(
            Supplier<BrowserType.LaunchOptions> browserLaunchOptions,
            PlaywrightTraceArchive playWrightTraceArchive,
            LottoProperties lottoProperties
    ) {
        this.browserLaunchOptions = browserLaunchOptions;
        this.playWrightTraceArchive = playWrightTraceArchive;
        this.lottoProperties = lottoProperties;
        this.sessionLimiter = new Semaphore(lottoProperties.getMaxConcurrentSessions(), true);
    }

    public LottoAutomationSession openSession() {
        acquirePermit();
        AtomicBoolean permitReleased = new AtomicBoolean(false);
        Runnable releasePermit = () -> {
            if (permitReleased.compareAndSet(false, true)) {
                sessionLimiter.release();
            }
        };

        Playwright playwright = null;
        Browser browser = null;
        BrowserContext context = null;
        try {
            playwright = Playwright.create();
            browser = playwright.chromium().launch(browserLaunchOptions.get());
            context = browser.newContext(new Browser.NewContextOptions()
                    .setUserAgent(LottoBrowserConstants.USER_AGENT_CHROME)
                    .setIsMobile(false));
            context.addInitScript(LottoBrowserConstants.SCRIPT_CHROME_PLATFORM);
            // 트레이스는 로그인 이후 startTracing()에서 시작한다(비밀번호 입력값이 트레이스에 남지 않도록).
            return new PlaywrightLottoSession(
                    context.newPage(),
                    context,
                    browser,
                    playwright,
                    playWrightTraceArchive,
                    new AtomicBoolean(false),
                    new AtomicBoolean(false),
                    new AtomicReference<>(),
                    releasePermit
            );
        } catch (RuntimeException exception) {
            cleanupFailedOpen(context, browser, playwright, releasePermit);
            throw exception;
        } catch (Error error) {
            cleanupFailedOpen(context, browser, playwright, releasePermit);
            throw error;
        }
    }

    /**
     * 로그인 이후에 트레이스를 시작한다. 트레이스는 진단용이므로 시작에 실패해도 작업은 계속한다.
     */
    public void startTracing(LottoAutomationSession session) {
        if (!(session instanceof PlaywrightLottoSession playwrightLottoSession)) {
            return;
        }
        if (!playwrightLottoSession.traceStarted().compareAndSet(false, true)) {
            return;
        }
        try {
            playWrightTraceArchive.start(playwrightLottoSession.context());
        } catch (RuntimeException exception) {
            playwrightLottoSession.traceStarted().set(false);
            log.warn("트레이스 시작 실패 - 트레이스 없이 계속 진행", exception);
        }
    }

    public Page requirePage(LottoAutomationSession session) {
        if (session instanceof PlaywrightLottoSession playwrightLottoSession) {
            return playwrightLottoSession.page();
        }
        throw new IllegalArgumentException("Unsupported session type: " + session.getClass().getName());
    }

    /**
     * 세마포어 대기를 무한정 하지 않고 작업 타임아웃 내로 제한한다. 모든 permit이 hung 세션에 잡혀 있어
     * 확보하지 못하면 예외로 종료해, future가 반드시 완료되고 in-flight 키가 해제되도록 한다(영구 skip 방지).
     */
    private void acquirePermit() {
        long timeoutMs = lottoProperties.getUserTaskTimeoutMs();
        try {
            if (!sessionLimiter.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException(
                        "브라우저 세션 허가를 " + timeoutMs + "ms 내에 확보하지 못했습니다 (동시 세션 한도 초과/지연)");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("브라우저 세션 허가 획득 중 인터럽트되었습니다", exception);
        }
    }

    private void cleanupFailedOpen(
            BrowserContext context,
            Browser browser,
            Playwright playwright,
            Runnable releasePermit
    ) {
        closeQuietly(context, "브라우저 컨텍스트 종료 실패");
        closeQuietly(browser, "브라우저 종료 실패");
        closeQuietly(playwright, "Playwright 종료 실패");
        releasePermit.run();
    }

    private record PlaywrightLottoSession(
            Page page,
            BrowserContext context,
            Browser browser,
            Playwright playwright,
            PlaywrightTraceArchive playWrightTraceArchive,
            AtomicBoolean failed,
            AtomicBoolean traceStarted,
            AtomicReference<Attachment> trace,
            Runnable releasePermit
    ) implements LottoAutomationSession {

        @Override
        public void markFailed() {
            failed.set(true);
        }

        @Override
        public Attachment failureTrace() {
            return trace.get();
        }

        @Override
        public void close() {
            try {
                trace.set(captureFailureArtifact());
            } finally {
                try {
                    closePage();
                    closeQuietly(context, "브라우저 컨텍스트 종료 실패");
                    closeQuietly(browser, "브라우저 종료 실패");
                    closeQuietly(playwright, "Playwright 종료 실패");
                } finally {
                    releasePermit.run();
                }
            }
        }

        /** 실패한 세션의 진단 자료: 로그인 후면 트레이스, 로그인 전이면 화면 캡처(트레이스는 시작 전). */
        private Attachment captureFailureArtifact() {
            if (traceStarted.get()) {
                return playWrightTraceArchive.stop(context, failed.get());
            }
            if (!failed.get() || page == null || page.isClosed()) {
                return null;
            }
            try {
                byte[] png = page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
                return new Attachment("lotto-failure-" + System.currentTimeMillis() + ".png", png, Attachment.PNG);
            } catch (RuntimeException exception) {
                PlaywrightSessionManager.log.warn("실패 화면 캡처 실패", exception);
                return null;
            }
        }

        private void closePage() {
            if (page == null) {
                return;
            }
            try {
                if (!page.isClosed()) {
                    page.close();
                }
            } catch (Exception exception) {
                PlaywrightSessionManager.log.warn("페이지 종료 실패", exception);
            }
        }
    }

    private static void closeQuietly(AutoCloseable closeable, String failureMessage) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception exception) {
            log.warn(failureMessage, exception);
        }
    }
}
