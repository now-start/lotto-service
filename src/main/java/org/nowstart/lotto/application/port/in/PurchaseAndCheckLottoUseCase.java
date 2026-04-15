package org.nowstart.lotto.application.port.in;

import org.nowstart.lotto.application.dto.PurchaseAndCheckLottoCommand;
import org.nowstart.lotto.domain.model.LottoExecution;

public interface PurchaseAndCheckLottoUseCase {

    LottoExecution purchaseAndCheck(PurchaseAndCheckLottoCommand command);
}
