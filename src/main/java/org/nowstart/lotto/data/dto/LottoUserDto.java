package org.nowstart.lotto.data.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LottoUserDto {
    String name;
    String deposit;

    public String toString() {
        return name + "의 💰예치금 : " + deposit;
    }
}
