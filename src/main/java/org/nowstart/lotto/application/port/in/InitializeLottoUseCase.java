package org.nowstart.lotto.application.port.in;

import org.nowstart.lotto.application.dto.InitializeLottoCommand;

public interface InitializeLottoUseCase {

    void initialize(InitializeLottoCommand command);
}
