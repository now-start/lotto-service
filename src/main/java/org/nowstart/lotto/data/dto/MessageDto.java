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

    @Builder.Default
    String alias = "Lotto";
    @Builder.Default
    String contentId = "lottoImage";
    @Builder.Default
    String imageText = "<img src='cid:lottoImage'/>";

    public String getImageText(){
        return text + "\n" + imageText;
    }
}
