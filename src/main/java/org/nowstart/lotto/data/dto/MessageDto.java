package org.nowstart.lotto.data.dto;

import lombok.Builder;
import lombok.Data;
import org.springframework.core.io.ByteArrayResource;

@Data
@Builder
public class MessageDto {

    String subject;
    String text;
    ByteArrayResource image;
    String to;

    @Builder.Default
    String alias = "Lotto";
    @Builder.Default
    String contentId = "image";
    @Builder.Default
    String imageText = "<img src='cid:image'/>";

    public String getImageText(){
        return text + "<br/><br/>" + imageText;
    }
}
