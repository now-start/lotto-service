package org.nowstart.lotto.adapter.out.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.application.port.out.LottoAutomationSession;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlaywrightSessionManager {

    private final Supplier<BrowserType.LaunchOptions> browserLaunchOptions;
    private final PlaywrightTraceArchive playWrightTraceArchive;

    public LottoAutomationSession openSession() {
        Playwright playwright = Playwright.create();
        Browser browser = null;
        BrowserContext context = null;
        try {
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
                    playWrightTraceArchive
            );
        } catch (Exception exception) {
            closeQuietly(context, "브라우저 컨텍스트 종료 실패");
            closeQuietly(browser, "브라우저 종료 실패");
            closeQuietly(playwright, "Playwright 종료 실패");
            throw exception;
        }
    }

    public Page requirePage(LottoAutomationSession session) {
        if (session instanceof PlaywrightLottoSession playwrightLottoSession) {
            return playwrightLottoSession.page();
        }
        throw new IllegalArgumentException("Unsupported session type: " + session.getClass().getName());
    }

    private record PlaywrightLottoSession(
            Page page,
            BrowserContext context,
            Browser browser,
            Playwright playwright,
            PlaywrightTraceArchive playWrightTraceArchive
    ) implements LottoAutomationSession {

        @Override
        public void close() {
            try {
                playWrightTraceArchive.stop(context);
            } finally {
                closePage();
                closeQuietly(context, "브라우저 컨텍스트 종료 실패");
                closeQuietly(browser, "브라우저 종료 실패");
                closeQuietly(playwright, "Playwright 종료 실패");
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
