package org.nowstart.lotto.application.port.in;

import org.nowstart.lotto.application.dto.ExecuteLottoCommand;
import org.nowstart.lotto.domain.model.LottoExecution;

public interface ExecuteLottoUseCase {

    LottoExecution execute(ExecuteLottoCommand command);
}
