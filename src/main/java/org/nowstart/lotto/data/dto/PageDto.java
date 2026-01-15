package org.nowstart.lotto.data.dto;

import com.microsoft.playwright.Page;
import java.util.function.Consumer;

public record PageDto(Page page, Consumer<Page> closeHandler) implements AutoCloseable {

    @Override
    public void close() {
        closeHandler.accept(page);
    }
}