package org.nowstart.lotto.data.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LottoResultDto {
    String rank;
    String amount;
}

