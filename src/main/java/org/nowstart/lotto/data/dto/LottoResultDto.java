package org.nowstart.lotto.data.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LottoResultDto {
    String deposit;
    String date;
    String round;
    String name;
    String number;
    String count;
    String result;
    String price;
}

