package org.nowstart.lotto.application.port.in;

import org.nowstart.lotto.application.dto.CheckLottoCommand;
import org.nowstart.lotto.application.dto.PurchaseLottoCommand;
import org.nowstart.lotto.domain.model.LottoExecution;

public interface LottoUseCase {

    LottoExecution check(CheckLottoCommand command);

    LottoExecution purchase(PurchaseLottoCommand command);
}
