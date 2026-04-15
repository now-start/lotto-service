package org.nowstart.lotto.application.service;

import lombok.RequiredArgsConstructor;
import org.nowstart.lotto.application.dto.PurchaseAndCheckLottoCommand;
import org.nowstart.lotto.application.port.in.PurchaseAndCheckLottoUseCase;
import org.nowstart.lotto.domain.model.LottoAccountSnapshot;
import org.nowstart.lotto.domain.model.LottoExecution;
import org.nowstart.lotto.domain.type.TaskMode;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PurchaseAndCheckLottoInteractor implements PurchaseAndCheckLottoUseCase {

    private final LottoExecutionRunner lottoExecutionRunner;

    @Override
    public LottoExecution purchaseAndCheck(PurchaseAndCheckLottoCommand command) {
        return lottoExecutionRunner.run(
                TaskMode.BUY_AND_CHECK,
                command.trigger(),
                command.userIds(),
                automationExecutor -> {
                    LottoAccountSnapshot accountSnapshot = automationExecutor.login();
                    automationExecutor.buy();
                    return new LottoUserExecutionContext(accountSnapshot, automationExecutor.check());
                }
        );
    }
}
