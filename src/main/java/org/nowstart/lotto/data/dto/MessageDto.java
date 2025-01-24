package org.nowstart.lotto.data.dto;

import lombok.Builder;
import lombok.Data;
import org.springframework.core.io.ByteArrayResource;

@Data
@Builder
public class MessageDto {

    String subject;
    String text;
    ByteArrayResource lottoImage;
}
