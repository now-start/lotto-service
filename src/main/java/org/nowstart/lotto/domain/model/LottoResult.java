package org.nowstart.lotto.domain.model;

import org.nowstart.lotto.domain.type.MessageType;

public record LottoResult(
        String date,
        String round,
        String name,
        String number,
        String count,
        String result,
        String price,
        byte[] imageBytes
) {
    public String summary() {
        MessageType messageType = MessageType.of(result);
        return messageType.getEmoji() + name + " " + round + "회차" + messageType.getEmoji();
    }
}
