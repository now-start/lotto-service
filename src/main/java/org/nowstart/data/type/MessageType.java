package org.nowstart.data.type;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum MessageType {
    WIN(""),
    NO_WIN("");

    private final String text;
}
