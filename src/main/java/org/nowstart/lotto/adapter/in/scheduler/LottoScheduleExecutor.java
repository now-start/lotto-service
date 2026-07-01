package org.nowstart.lotto.adapter.in.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.application.port.in.LottoUseCase;
import org.nowstart.lotto.application.port.in.LottoUseCase.TargetCommand;
import org.nowstart.lotto.domain.type.TriggerType;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * local 프로파일에서는 비활성화한다 — 로컬 실행 중 스케줄러가 실제 구매/확인을 자동 트리거하는 사고를 막기 위함.
 */
@Slf4j
@Component
@Profile("!local")
@RequiredArgsConstructor
public class LottoScheduleExecutor {

    private final LottoUseCase lottoUseCase;

    @Scheduled(cron = "${lotto.cron.check}", zone = "${lotto.cron.zone:Asia/Seoul}")
    public void checkLottoResults() {
        log.info("[Schedule] Check Start");
        lottoUseCase.check(TargetCommand.all(TriggerType.SCHEDULE));
    }

    @Scheduled(cron = "${lotto.cron.buy}", zone = "${lotto.cron.zone:Asia/Seoul}")
    public void buyLottoTickets() {
        log.info("[Schedule] Purchase Start");
        lottoUseCase.purchase(TargetCommand.all(TriggerType.SCHEDULE));
    }
}
