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
 * 동적 Trigger로 등록하되 직접 스케줄을 관리한다.
 * - /actuator/refresh(RefreshScopeRefreshedEvent) 시 기존 예약을 취소하고 즉시 재등록한다.
 * - 재등록 시 generation을 올려, cancel(false)로 못 막은 실행 중 이전 트리거가 재예약 시 null을 반환하게 하여 중복 발화를 막는다.
 * - 재등록 전 새 cron/zone을 검증하고, 등록 시점의 '검증된' CronTrigger를 캡처한다.
 * - 초기(@PostConstruct) 등록이 실패하면 유지할 이전 스케줄이 없으므로 fail-fast(예외 전파)한다.
 *   이후 refresh 실패는 마지막 정상 스케줄을 유지한다.
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
        reschedule(true);
    }

    @EventListener(RefreshScopeRefreshedEvent.class)
    public synchronized void onRefresh(RefreshScopeRefreshedEvent event) {
        log.info("[Schedule] Refresh detected - rescheduling with current cron");
        reschedule(false);
    }

    @PreDestroy
    public synchronized void shutdown() {
        generation.incrementAndGet();
        cancelAll();
    }

    private void reschedule(boolean failFastOnInvalid) {
        String check = lottoProperties.getCron().getCheck();
        String buy = lottoProperties.getCron().getBuy();
        String zoneId = lottoProperties.getCron().getZone();

        // 취소/재등록 전에 새 cron·zone을 검증하고, 유효한 값만 캡처한다.
        CronTrigger checkTrigger;
        CronTrigger buyTrigger;
        try {
            ZoneId zone = ZoneId.of(zoneId);
            checkTrigger = new CronTrigger(check, zone);
            buyTrigger = new CronTrigger(buy, zone);
        } catch (RuntimeException exception) {
            if (failFastOnInvalid) {
                // 초기 등록 실패: 유지할 이전 스케줄이 없으므로 fail-fast — 스케줄러가 조용히 비활성화되는 것을 막는다.
                throw new IllegalStateException(
                        "초기 스케줄 cron/zone 설정이 유효하지 않습니다 (check=" + check + ", buy=" + buy
                                + ", zone=" + zoneId + ")", exception);
            }
            log.error("[Schedule] Invalid cron/zone on refresh - keeping previous schedule (check={}, buy={}, zone={})",
                    check, buy, zoneId, exception);
            return;
        }

        long currentGeneration = generation.incrementAndGet();
        cancelAll();
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
                return null;
            }
            return cronTrigger.nextExecution(triggerContext);
        };
    }
}
