package org.nowstart.lotto.adapter.in.scheduler;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.application.port.in.LottoUseCase;
import org.nowstart.lotto.application.port.in.LottoUseCase.TargetCommand;
import org.nowstart.lotto.config.LottoProperties;
import org.nowstart.lotto.domain.type.TriggerType;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

/**
 * 동적 Trigger로 등록하되, cron 변경 반영을 위해 직접 스케줄을 관리한다.
 * - /actuator/refresh(RefreshScopeRefreshedEvent) 시 기존 예약을 취소하고 즉시 재등록한다.
 * - 재등록 시 generation을 올려, 실행 중이라 cancel(false)로 못 막은 이전 트리거가 재예약을 시도할 때
 *   nextExecution이 null을 반환하도록 하여 옛/새 cron이 동시에 발화하는 중복 실행을 방지한다.
 * - 재등록 전 새 cron/zone을 검증하고, 잘못되면 기존 스케줄을 유지한다. 이때 유지되는 트리거는 등록 시점에
 *   '검증된' cron/zone을 캡처해 사용하므로, live(무효) 값을 다시 읽어 실패하는 일이 없다(마지막 정상 스케줄 유지).
 *
 * local 프로파일에서는 비활성화한다 — 로컬 실행 중 스케줄러가 실제 구매/확인을 자동 트리거하는 사고를 막기 위함.
 */
@Slf4j
@Component
@Profile("!local")
@RequiredArgsConstructor
public class LottoScheduleExecutor {

    private final LottoUseCase lottoUseCase;
    private final LottoProperties lottoProperties;
    private final TaskScheduler taskScheduler;

    private final List<ScheduledFuture<?>> scheduledTasks = new CopyOnWriteArrayList<>();
    private final AtomicLong generation = new AtomicLong(0);

    @PostConstruct
    public synchronized void scheduleAll() {
        reschedule();
    }

    @EventListener(RefreshScopeRefreshedEvent.class)
    public synchronized void onRefresh(RefreshScopeRefreshedEvent event) {
        log.info("[Schedule] Refresh detected - rescheduling with current cron");
        reschedule();
    }

    @PreDestroy
    public synchronized void shutdown() {
        generation.incrementAndGet();
        cancelAll();
    }

    private void reschedule() {
        String check = lottoProperties.getCron().getCheck();
        String buy = lottoProperties.getCron().getBuy();
        String zoneId = lottoProperties.getCron().getZone();

        // 취소/재등록 전에 새 cron·zone을 검증하고, 유효한 값만 캡처한다. 잘못되면 기존 스케줄을 유지한다.
        CronTrigger checkTrigger;
        CronTrigger buyTrigger;
        try {
            ZoneId zone = ZoneId.of(zoneId);
            checkTrigger = new CronTrigger(check, zone);
            buyTrigger = new CronTrigger(buy, zone);
        } catch (RuntimeException exception) {
            log.error("[Schedule] Invalid cron/zone - keeping previous schedule (check={}, buy={}, zone={})",
                    check, buy, zoneId, exception);
            return;
        }

        long currentGeneration = generation.incrementAndGet();
        cancelAll();
        // 트리거에 '검증된' CronTrigger 인스턴스를 캡처해 넘긴다 — 이후 live 프로퍼티(무효일 수 있음)를 다시 읽지 않는다.
        scheduledTasks.add(taskScheduler.schedule(this::checkLottoResults,
                generationAwareTrigger(currentGeneration, checkTrigger)));
        scheduledTasks.add(taskScheduler.schedule(this::buyLottoTickets,
                generationAwareTrigger(currentGeneration, buyTrigger)));
        log.info("[Schedule] Registered check/buy triggers (gen={}, check={}, buy={}, zone={})",
                currentGeneration, check, buy, zoneId);
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

    private Trigger generationAwareTrigger(long ownedGeneration, CronTrigger cronTrigger) {
        return (TriggerContext triggerContext) -> {
            if (ownedGeneration != generation.get()) {
                // refresh로 대체된(오래된) 트리거 — 재예약을 중단해 중복 발화를 막는다.
                return null;
            }
            return cronTrigger.nextExecution(triggerContext);
        };
    }
}
