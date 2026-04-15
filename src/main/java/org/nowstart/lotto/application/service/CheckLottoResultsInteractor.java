package org.nowstart.lotto.application.service;

import lombok.RequiredArgsConstructor;
import org.nowstart.lotto.application.dto.CheckLottoResultsCommand;
import org.nowstart.lotto.application.port.in.CheckLottoResultsUseCase;
import org.nowstart.lotto.domain.model.LottoExecution;
import org.nowstart.lotto.domain.type.TaskMode;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CheckLottoResultsInteractor implements CheckLottoResultsUseCase {

    private final LottoExecutionRunner lottoExecutionRunner;

    @Override
    public LottoExecution check(CheckLottoResultsCommand command) {
        return lottoExecutionRunner.run(
                TaskMode.CHECK_ONLY,
                command.trigger(),
                command.userIds(),
                automationExecutor -> new LottoUserExecutionContext(
                        automationExecutor.login(),
                        automationExecutor.check()
                )
        );
    }
}
