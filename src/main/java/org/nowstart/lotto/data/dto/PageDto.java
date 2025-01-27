package org.nowstart.lotto.data.dto;

import com.microsoft.playwright.*;
import lombok.Data;

import java.nio.file.Paths;
import java.time.LocalDate;

@Data
public class PageDto implements AutoCloseable {

    BrowserContext context;
    Page page;

    public PageDto(Browser browser) {
        context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36"));
        context.tracing().start(new Tracing.StartOptions()
                .setScreenshots(true)
                .setSnapshots(true)
                .setSources(true));
        page = context.newPage();
    }

    @Override
    public void close() {
        context.tracing().stop(new Tracing.StopOptions().setPath(Paths.get("log/" + LocalDate.now() + "-trace.zip")));
        context.close();
        page.close();
    }
}
