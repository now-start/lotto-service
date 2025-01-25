package org.nowstart.lotto.data.dto;

import lombok.Builder;
import lombok.Data;
import org.nowstart.lotto.data.type.MessageType;

@Data
@Builder
public class LottoResultDto {
    String date;
    String round;
    String name;
    String number;
    String count;
    String result;
    String price;

    public String toString() {
        return MessageType.of(result).getEmoji() + name + " " + round + "회차" + MessageType.of(result).getEmoji();
    }
}

