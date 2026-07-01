package org.nowstart.lotto.adapter.in.scheduler;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.application.port.in.LottoUseCase;
import org.nowstart.lotto.application.port.in.LottoUseCase.TargetCommand;
import org.nowstart.lotto.config.LottoProperties;
import org.nowstart.lotto.domain.type.TriggerType;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

/**
 * 동적 Trigger로 등록하되, cron 변경 반영을 위해 직접 스케줄을 관리한다.
 * - 매 실행 계산 시점에 LottoProperties의 현재 cron/zone을 다시 읽는다.
 * - /actuator/refresh 시 발생하는 RefreshScopeRefreshedEvent를 수신해 기존 예약을 취소하고 즉시 재등록한다.
 *   (재등록하지 않으면 이미 계산된 다음 실행은 옛 cron으로 유지되고, 그 이후 실행부터만 새 cron이 반영된다.)
 *
 * local 프로파일에서는 비활성화한다 — 로컬 실행 중 스케줄러가 실제 구매/확인을 자동 트리거하는 사고를 막기 위함.
 */
@Slf4j
@Component
@org.springframework.context.annotation.Profile("!local")
@RequiredArgsConstructor
public class LottoScheduleExecutor {

    private final LottoUseCase lottoUseCase;
    private final LottoProperties lottoProperties;
    private final TaskScheduler taskScheduler;

    private final List<ScheduledFuture<?>> scheduledTasks = new CopyOnWriteArrayList<>();

    @PostConstruct
    public synchronized void scheduleAll() {
        cancelAll();
        scheduledTasks.add(taskScheduler.schedule(this::checkLottoResults,
                triggerFor(() -> lottoProperties.getCron().getCheck())));
        scheduledTasks.add(taskScheduler.schedule(this::buyLottoTickets,
                triggerFor(() -> lottoProperties.getCron().getBuy())));
        log.info("[Schedule] Registered check/buy triggers (check={}, buy={}, zone={})",
                lottoProperties.getCron().getCheck(),
                lottoProperties.getCron().getBuy(),
                lottoProperties.getCron().getZone());
    }

    @EventListener(RefreshScopeRefreshedEvent.class)
    public synchronized void onRefresh(RefreshScopeRefreshedEvent event) {
        log.info("[Schedule] Refresh detected - rescheduling with current cron");
        scheduleAll();
    }

    @PreDestroy
    public synchronized void shutdown() {
        cancelAll();
    }

    void checkLottoResults() {
        log.info("[Schedule] Check Start");
        lottoUseCase.check(TargetCommand.all(TriggerType.SCHEDULE));
    }

    void buyLottoTickets() {
        log.info("[Schedule] Purchase Start");
        lottoUseCase.purchase(TargetCommand.all(TriggerType.SCHEDULE));
    }

    private void cancelAll() {
        scheduledTasks.forEach(task -> task.cancel(false));
        scheduledTasks.clear();
    }

    private Trigger triggerFor(Supplier<String> cronExpressionSupplier) {
        return (TriggerContext triggerContext) -> nextExecution(cronExpressionSupplier.get(), triggerContext);
    }

    private Instant nextExecution(String cronExpression, TriggerContext triggerContext) {
        ZoneId zone = ZoneId.of(lottoProperties.getCron().getZone());
        return new CronTrigger(cronExpression, zone).nextExecution(triggerContext);
    }
}
