package org.nowstart.lotto.adapter.outbound.mail

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import org.nowstart.lotto.application.port.outbound.NotificationMessage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

private val log = LoggerFactory.getLogger(MailOutbox::class.java)

/**
 * 일시 장애로 보내지 못한 메일을 메모리에 보관하고 주기적으로 다시 보낸다.
 * 캐시가 아니므로 축출하지 않는다. 보관 기간을 넘기거나 대기열 상한을 넘을 때만 버리고 ERROR로 남긴다.
 * 재시작하면 보관분은 사라진다(단일 인스턴스·DB 없음 전제).
 */
@Component
class MailOutbox(
    private val deliverer: MailDeliverer,
    private val clock: Clock,
    private val meterRegistry: MeterRegistry,
    startScheduler: Boolean,
) : DisposableBean {

    @Autowired
    constructor(deliverer: MailDeliverer, clock: Clock, meterRegistry: MeterRegistry) : this(deliverer, clock, meterRegistry, true)

    private val pending = ConcurrentLinkedDeque<Pending>()
    private val resendLock = Any()
    private val scheduler: ScheduledExecutorService? = if (startScheduler) {
        // cron과 같은 스케줄러 스레드를 쓰면 SMTP 타임아웃 동안 cron이 밀리므로 전용 스레드를 쓴다.
        Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("mail-outbox").factory()).also {
            val intervalMs = RESEND_INTERVAL.toMillis()
            it.scheduleWithFixedDelay(::resendDueSafely, intervalMs, intervalMs, TimeUnit.MILLISECONDS)
        }
    } else {
        null
    }

    init {
        meterRegistry.gaugeCollectionSize("lotto.notification.outbox.size", Tags.empty(), pending)
    }

    fun enqueue(message: NotificationMessage) {
        while (pending.size >= MAX_PENDING) {
            val dropped = pending.pollFirst() ?: break
            meterRegistry.counter("lotto.notification.failed", "reason", "outbox_overflow").increment()
            log.error("[Mail][Outbox] Dropped oldest pending mail (queue full {}) to: {}, subject: {}",
                MAX_PENDING, maskAddress(dropped.message.to), dropped.message.subject)
        }
        pending.addLast(Pending(message, clock.instant()))
        log.warn("[Mail][Outbox] Queued for resend every {} (pending={}) to: {}, subject: {}",
            RESEND_INTERVAL, pending.size, maskAddress(message.to), message.subject)
    }

    fun size(): Int = pending.size

    /**
     * 보관 중인 메일을 오래된 순서로 다시 보낸다. 한 건이 계속 실패해도 뒤 메일을 막지 않도록 다음 건으로 넘어가되,
     * 서버 장애로 연속 실패하면(타임아웃이 쌓이지 않게) 이번 회차는 중단한다.
     */
    fun resendDue() {
        synchronized(resendLock) {
            val now = clock.instant()
            var consecutiveFailures = 0
            for (item in pending.toList()) {
                if (Duration.between(item.queuedAt, now) > MAX_AGE) {
                    pending.remove(item)
                    meterRegistry.counter("lotto.notification.failed", "reason", "expired").increment()
                    log.error("[Mail][Outbox] Gave up after {} to: {}, subject: {}", MAX_AGE, maskAddress(item.message.to), item.message.subject)
                    continue
                }
                val result = deliverer.deliver(item.message)
                when {
                    result.isSent -> {
                        pending.remove(item)
                        consecutiveFailures = 0
                        log.info("[Mail][Outbox] Resent (pending={}) subject: {}", pending.size, item.message.subject)
                    }
                    result.kind == SmtpFailureKind.PERMANENT -> {
                        pending.remove(item)
                        consecutiveFailures = 0
                        log.error("[Mail][Outbox] Permanent failure, dropped subject: {}", item.message.subject)
                    }
                    ++consecutiveFailures >= MAX_CONSECUTIVE_FAILURES_PER_RUN -> {
                        log.warn("[Mail][Outbox] Still failing - retry in {} (pending={})", RESEND_INTERVAL, pending.size)
                        return
                    }
                }
            }
        }
    }

    private fun resendDueSafely() {
        try {
            resendDue()
        } catch (exception: RuntimeException) {
            log.error("[Mail][Outbox] Resend run failed", exception)
        }
    }

    override fun destroy() {
        scheduler?.shutdownNow()
        if (pending.isNotEmpty()) {
            log.error("[Mail][Outbox] Shutting down with {} unsent mail(s); they are lost", pending.size)
        }
    }

    /** 같은 내용의 메일이 여러 건 쌓여도 한 건만 지우도록 동일성(identity)으로 비교한다. */
    private class Pending(val message: NotificationMessage, val queuedAt: Instant)

    companion object {
        val RESEND_INTERVAL: Duration = Duration.ofMinutes(10)
        val MAX_AGE: Duration = Duration.ofHours(48)
        const val MAX_PENDING = 200

        /** 한 번의 재발송에서 이만큼 연속 일시 실패하면 서버 장애로 보고 다음 주기로 미룬다. */
        const val MAX_CONSECUTIVE_FAILURES_PER_RUN = 3
    }
}
