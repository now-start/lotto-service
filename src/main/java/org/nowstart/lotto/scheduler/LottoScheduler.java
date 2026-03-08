package org.nowstart.lotto.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.data.type.TaskMode;
import org.nowstart.lotto.data.type.TriggerType;
import org.nowstart.lotto.service.LottoService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LottoScheduler {

    private final LottoService lottoService;

    @Scheduled(cron = "${lotto.cron.check}")
    public void checkLottoResults() {
        log.info("[Schedule] Check Start");
        lottoService.execute(TaskMode.CHECK_ONLY, TriggerType.SCHEDULE, null);
    }

    @Scheduled(cron = "${lotto.cron.buy}")
    public void buyLottoTickets() {
        log.info("[Schedule] Purchase Start");
        lottoService.execute(TaskMode.BUY_AND_CHECK, TriggerType.SCHEDULE, null);
    }
}
