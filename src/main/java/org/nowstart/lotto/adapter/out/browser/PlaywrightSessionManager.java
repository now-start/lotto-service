package org.nowstart.lotto.adapter.out.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final Semaphore sessionLimiter;

    public PlaywrightSessionManager(
            Supplier<BrowserType.LaunchOptions> browserLaunchOptions,
            PlaywrightTraceArchive playWrightTraceArchive,
            LottoProperties lottoProperties
    ) {
        this.browserLaunchOptions = browserLaunchOptions;
        this.playWrightTraceArchive = playWrightTraceArchive;
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
            playWrightTraceArchive.start(context);
            return new PlaywrightLottoSession(
                    context.newPage(),
                    context,
                    browser,
                    playwright,
                    playWrightTraceArchive,
                    releasePermit
            );
        } catch (Exception exception) {
            closeQuietly(context, "브라우저 컨텍스트 종료 실패");
            closeQuietly(browser, "브라우저 종료 실패");
            closeQuietly(playwright, "Playwright 종료 실패");
            releasePermit.run();
            throw exception;
        }
    }

    public Page requirePage(LottoAutomationSession session) {
        if (session instanceof PlaywrightLottoSession playwrightLottoSession) {
            return playwrightLottoSession.page();
        }
        throw new IllegalArgumentException("Unsupported session type: " + session.getClass().getName());
    }

    private void acquirePermit() {
        try {
            sessionLimiter.acquire();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("브라우저 세션 허가 획득 중 인터럽트되었습니다", exception);
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
