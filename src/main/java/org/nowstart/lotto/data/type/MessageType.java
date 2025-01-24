package org.nowstart.lotto.data.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MessageType {
    WAITE("미추첨", "⏳"),
    WIN("당첨", "🎉"),
    NO_WIN("낙첨", "☠️"),
    UNDEFINED("", "");

    private final String text;
    private final String emoji;

    public static MessageType of(String text) {
        for (MessageType messageType : values()) {
            if (messageType.text.equals(text)) {
                return messageType;
            }
        }
        return UNDEFINED;
    }
}
