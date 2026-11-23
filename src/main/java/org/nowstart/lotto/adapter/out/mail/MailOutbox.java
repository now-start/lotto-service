package org.nowstart.lotto.adapter.out.mail;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.application.port.out.SendNotificationPort.NotificationMessage;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 일시 장애로 보내지 못한 메일을 메모리에 보관하고 주기적으로 다시 보낸다.
 * 캐시가 아니므로 축출하지 않는다. 보관 기간을 넘기거나 대기열 상한을 넘을 때만 버리고 ERROR로 남긴다.
 * 재시작하면 보관분은 사라진다(단일 인스턴스·DB 없음 전제).
 */
@Slf4j
@Component
class MailOutbox implements DisposableBean {

    static final Duration RESEND_INTERVAL = Duration.ofMinutes(10);
    static final Duration MAX_AGE = Duration.ofHours(48);
    static final int MAX_PENDING = 200;
    /** 한 번의 재발송에서 이만큼 연속 일시 실패하면 서버 장애로 보고 다음 주기로 미룬다. */
    static final int MAX_CONSECUTIVE_FAILURES_PER_RUN = 3;

    private final ConcurrentLinkedDeque<Pending> pending = new ConcurrentLinkedDeque<>();
    private final MailDeliverer deliverer;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final ScheduledExecutorService scheduler;
    private final Object resendLock = new Object();

    @Autowired
    MailOutbox(MailDeliverer deliverer, Clock clock, MeterRegistry meterRegistry) {
        this(deliverer, clock, meterRegistry, true);
    }

    MailOutbox(MailDeliverer deliverer, Clock clock, MeterRegistry meterRegistry, boolean startScheduler) {
        this.deliverer = deliverer;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        meterRegistry.gaugeCollectionSize("lotto.notification.outbox.size", List.of(), pending);
        if (startScheduler) {
            // cron과 같은 스케줄러 스레드를 쓰면 SMTP 타임아웃 동안 cron이 밀리므로 전용 스레드를 쓴다.
            this.scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("mail-outbox").factory());
            long intervalMs = RESEND_INTERVAL.toMillis();
            this.scheduler.scheduleWithFixedDelay(this::resendDueSafely, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        } else {
            this.scheduler = null;
        }
    }

    void enqueue(NotificationMessage message) {
        while (pending.size() >= MAX_PENDING) {
            Pending dropped = pending.pollFirst();
            if (dropped != null) {
                meterRegistry.counter("lotto.notification.failed", "reason", "outbox_overflow").increment();
                log.error("[Mail][Outbox] Dropped oldest pending mail (queue full {}) to: {}, subject: {}",
                        MAX_PENDING, MailAddresses.mask(dropped.message().to()), dropped.message().subject());
            }
        }
        pending.addLast(new Pending(message, clock.instant()));
        log.warn("[Mail][Outbox] Queued for resend every {} (pending={}) to: {}, subject: {}",
                RESEND_INTERVAL, pending.size(), MailAddresses.mask(message.to()), message.subject());
    }

    int size() {
        return pending.size();
    }

    /**
     * 보관 중인 메일을 오래된 순서로 다시 보낸다. 한 건이 계속 실패해도 뒤 메일을 막지 않도록 다음 건으로 넘어가되,
     * 서버 장애로 연속 실패하면(타임아웃이 쌓이지 않게) 이번 회차는 중단한다.
     */
    void resendDue() {
        synchronized (resendLock) {
            Instant now = clock.instant();
            int consecutiveFailures = 0;
            for (Pending item : List.copyOf(pending)) {
                if (Duration.between(item.queuedAt(), now).compareTo(MAX_AGE) > 0) {
                    pending.remove(item);
                    meterRegistry.counter("lotto.notification.failed", "reason", "expired").increment();
                    log.error("[Mail][Outbox] Gave up after {} to: {}, subject: {}",
                            MAX_AGE, MailAddresses.mask(item.message().to()), item.message().subject());
                    continue;
                }
                MailDeliverer.Result result = deliverer.deliver(item.message());
                if (result.isSent()) {
                    pending.remove(item);
                    consecutiveFailures = 0;
                    log.info("[Mail][Outbox] Resent (pending={}) subject: {}", pending.size(), item.message().subject());
                } else if (result.kind() == SmtpFailureKind.PERMANENT) {
                    pending.remove(item);
                    consecutiveFailures = 0;
                    log.error("[Mail][Outbox] Permanent failure, dropped subject: {}", item.message().subject());
                } else if (++consecutiveFailures >= MAX_CONSECUTIVE_FAILURES_PER_RUN) {
                    log.warn("[Mail][Outbox] Still failing - retry in {} (pending={})", RESEND_INTERVAL, pending.size());
                    return;
                }
            }
        }
    }

    private void resendDueSafely() {
        try {
            resendDue();
        } catch (RuntimeException exception) {
            log.error("[Mail][Outbox] Resend run failed", exception);
        }
    }

    @Override
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (!pending.isEmpty()) {
            log.error("[Mail][Outbox] Shutting down with {} unsent mail(s); they are lost", pending.size());
        }
    }

    private record Pending(NotificationMessage message, Instant queuedAt) {
    }
}
