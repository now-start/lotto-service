package org.nowstart.lotto.data.dto;

import lombok.Builder;
import lombok.Data;
import org.nowstart.lotto.data.type.MessageType;
import org.springframework.core.io.ByteArrayResource;

@Data
@Builder
public class ResultDto {
    String date;
    String round;
    String name;
    String number;
    String count;
    String result;
    String price;
    ByteArrayResource image;

    public String toString() {
        return MessageType.of(result).getEmoji() + name + " " + round + "회차" + MessageType.of(result).getEmoji();
    }
}

