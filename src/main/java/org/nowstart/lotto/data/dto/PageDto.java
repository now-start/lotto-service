package org.nowstart.lotto.data.dto;

import com.microsoft.playwright.Page;
import org.nowstart.lotto.service.PageService;

public record PageDto(Page page, PageService pageService) implements AutoCloseable {

    @Override
    public void close() {
        pageService.closePage(page);
    }
}