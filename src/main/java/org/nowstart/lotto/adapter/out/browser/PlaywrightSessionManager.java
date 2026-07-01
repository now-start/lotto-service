package org.nowstart.lotto.adapter.out.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.application.port.out.LottoAutomationSession;
import org.nowstart.lotto.config.LottoProperties;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PlaywrightSessionManager {

    private final Supplier<BrowserType.LaunchOptions> browserLaunchOptions;
    private final PlaywrightTraceArchive playWrightTraceArchive;
    private final LottoProperties lottoProperties;
    private final ResizableSemaphore sessionLimiter;

    public PlaywrightSessionManager(
            Supplier<BrowserType.LaunchOptions> browserLaunchOptions,
            PlaywrightTraceArchive playWrightTraceArchive,
            LottoProperties lottoProperties
    ) {
        this.browserLaunchOptions = browserLaunchOptions;
        this.playWrightTraceArchive = playWrightTraceArchive;
        this.lottoProperties = lottoProperties;
        this.sessionLimiter = new ResizableSemaphore(lottoProperties.getMaxConcurrentSessions());
    }

    @EventListener(RefreshScopeRefreshedEvent.class)
    public void onRefresh(RefreshScopeRefreshedEvent event) {
        int newLimit = lottoProperties.getMaxConcurrentSessions();
        sessionLimiter.resize(newLimit);
        log.info("[Session] max-concurrent-sessions resized to {}", newLimit);
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
            playWrightTraceArchive.start(context);
            return new PlaywrightLottoSession(
                    context.newPage(),
                    context,
                    browser,
                    playwright,
                    playWrightTraceArchive,
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

    /** 런타임에 permit 수를 조정할 수 있는 세마포어. */
    private static final class ResizableSemaphore extends Semaphore {

        private int currentPermits;

        ResizableSemaphore(int permits) {
            super(permits, true);
            this.currentPermits = permits;
        }

        synchronized void resize(int newPermits) {
            if (newPermits < 1) {
                log.warn("[Session] 무시된 잘못된 max-concurrent-sessions 값: {}", newPermits);
                return;
            }
            int delta = newPermits - currentPermits;
            if (delta > 0) {
                release(delta);
            } else if (delta < 0) {
                reducePermits(-delta);
            }
            currentPermits = newPermits;
        }
    }

    private record PlaywrightLottoSession(
            Page page,
            BrowserContext context,
            Browser browser,
            Playwright playwright,
            PlaywrightTraceArchive playWrightTraceArchive,
            Runnable releasePermit
    ) implements LottoAutomationSession {

        @Override
        public void close() {
            try {
                playWrightTraceArchive.stop(context);
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
