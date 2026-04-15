package org.nowstart.lotto.application.port.in;

import org.nowstart.lotto.application.dto.CheckLottoResultsCommand;
import org.nowstart.lotto.domain.model.LottoExecution;

public interface CheckLottoResultsUseCase {

    LottoExecution check(CheckLottoResultsCommand command);
}
