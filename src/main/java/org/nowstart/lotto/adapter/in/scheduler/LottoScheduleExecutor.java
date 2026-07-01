package org.nowstart.lotto.adapter.in.scheduler;

import java.time.Instant;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.application.port.in.LottoUseCase;
import org.nowstart.lotto.application.port.in.LottoUseCase.TargetCommand;
import org.nowstart.lotto.config.LottoProperties;
import org.nowstart.lotto.domain.type.TriggerType;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

/**
 * 정적 @Scheduled 대신 동적 Trigger로 등록한다. 매 실행 계산 시점에 LottoProperties의 현재 cron을 다시 읽으므로,
 * Config Server refresh(@ConfigurationProperties 재바인딩) 이후 변경된 cron이 다음 실행부터 반영된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LottoScheduleExecutor implements SchedulingConfigurer {

    private final LottoUseCase lottoUseCase;
    private final LottoProperties lottoProperties;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(
                this::checkLottoResults,
                triggerContext -> nextExecution(lottoProperties.getCron().getCheck(), triggerContext));
        taskRegistrar.addTriggerTask(
                this::buyLottoTickets,
                triggerContext -> nextExecution(lottoProperties.getCron().getBuy(), triggerContext));
    }

    void checkLottoResults() {
        log.info("[Schedule] Check Start");
        lottoUseCase.check(TargetCommand.all(TriggerType.SCHEDULE));
    }

    void buyLottoTickets() {
        log.info("[Schedule] Purchase Start");
        lottoUseCase.purchase(TargetCommand.all(TriggerType.SCHEDULE));
    }

    private Instant nextExecution(String cronExpression, TriggerContext triggerContext) {
        ZoneId zone = ZoneId.of(lottoProperties.getCron().getZone());
        return new CronTrigger(cronExpression, zone).nextExecution(triggerContext);
    }
}
