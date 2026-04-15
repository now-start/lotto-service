package org.nowstart.lotto.adapter.in.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.application.port.in.LottoUseCase;
import org.nowstart.lotto.application.port.in.LottoUseCase.TargetCommand;
import org.nowstart.lotto.domain.type.TriggerType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LottoScheduleExecutor {

    private final LottoUseCase lottoUseCase;

    @Scheduled(cron = "${lotto.cron.check}")
    public void checkLottoResults() {
        log.info("[Schedule] Check Start");
        lottoUseCase.check(TargetCommand.all(TriggerType.SCHEDULE));
    }

    @Scheduled(cron = "${lotto.cron.buy}")
    public void buyLottoTickets() {
        log.info("[Schedule] Purchase Start");
        lottoUseCase.purchase(TargetCommand.all(TriggerType.SCHEDULE));
    }
}
