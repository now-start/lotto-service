package org.nowstart.lotto.adapter.out.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.application.port.out.LottoAutomationSession;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlaywrightSessionManager {

    private final Browser browser;
    private final PlaywrightTraceArchive playWrightTraceArchive;

    public LottoAutomationSession openSession() {
        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent(LottoBrowserConstants.USER_AGENT_CHROME)
                .setIsMobile(false));

        context.addInitScript(LottoBrowserConstants.SCRIPT_CHROME_PLATFORM);
        playWrightTraceArchive.start(context);
        return new PlaywrightLottoSession(context.newPage(), context, playWrightTraceArchive);
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
            PlaywrightTraceArchive playWrightTraceArchive
    ) implements LottoAutomationSession {

        @Override
        public void close() {
            if (page == null || page.isClosed()) {
                return;
            }

            try {
                playWrightTraceArchive.stop(context);
            } finally {
                try {
                    if (!page.isClosed()) {
                        page.close();
                    }
                    context.close();
                } catch (Exception exception) {
                    PlaywrightSessionManager.log.warn("리소스 종료 실패", exception);
                }
            }
        }
    }
}
