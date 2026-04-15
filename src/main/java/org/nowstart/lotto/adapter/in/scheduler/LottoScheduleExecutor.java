package org.nowstart.lotto.adapter.in.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.application.dto.ExecuteLottoCommand;
import org.nowstart.lotto.application.port.in.ExecuteLottoUseCase;
import org.nowstart.lotto.domain.type.TaskMode;
import org.nowstart.lotto.domain.type.TriggerType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LottoScheduleExecutor {

    private final ExecuteLottoUseCase executeLottoUseCase;

    @Scheduled(cron = "${lotto.cron.check}")
    public void checkLottoResults() {
        log.info("[Schedule] Check Start");
        executeLottoUseCase.execute(new ExecuteLottoCommand(TaskMode.CHECK_ONLY, TriggerType.SCHEDULE, null));
    }

    @Scheduled(cron = "${lotto.cron.buy}")
    public void buyLottoTickets() {
        log.info("[Schedule] Purchase Start");
        executeLottoUseCase.execute(new ExecuteLottoCommand(TaskMode.BUY_AND_CHECK, TriggerType.SCHEDULE, null));
    }
}
