package org.nowstart.lotto.application.service

import org.nowstart.lotto.application.port.outbound.Attachment
import org.nowstart.lotto.application.port.outbound.CheckResult
import org.nowstart.lotto.application.port.outbound.Delivery
import org.nowstart.lotto.application.port.outbound.LottoAccountSnapshot
import org.nowstart.lotto.application.port.outbound.LottoUser
import org.nowstart.lotto.application.port.outbound.NotificationMessage
import org.nowstart.lotto.application.port.outbound.SendNotificationPort
import org.nowstart.lotto.domain.type.TaskMode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

private val log = LoggerFactory.getLogger(JobNotifier::class.java)

/** 작업의 최종 결과만 통지한다. 통지 실패는 작업 결과를 바꾸지 않는다. */
@Component
class JobNotifier(
    private val lottoNotificationFactory: LottoNotificationFactory,
    private val sendNotificationPort: SendNotificationPort,
) {

    fun purchase(user: LottoUser, account: LottoAccountSnapshot?, outcome: PurchaseOutcome, trace: Attachment?) {
        val message = build(user) { lottoNotificationFactory.createPurchaseOutcomeMessage(user, account, outcome) } ?: return
        send(user, if (outcome.needsDiagnostics) message.withAttachment(trace) else message, outcome.javaClass.simpleName)
    }

    fun check(user: LottoUser, account: LottoAccountSnapshot, results: List<CheckResult>) {
        build(user) { lottoNotificationFactory.createCheckSuccessMessage(user, account, results) }?.let { send(user, it, "check") }
    }

    fun failure(user: LottoUser, mode: TaskMode, error: Exception, trace: Attachment?) {
        build(user) { lottoNotificationFactory.createFailureMessage(user, mode, error) }
            ?.let { send(user, it.withAttachment(trace), "failure") }
    }

    private fun build(user: LottoUser, factory: () -> NotificationMessage?): NotificationMessage? = try {
        factory()
    } catch (exception: RuntimeException) {
        log.error("[Notify][{}] notification build failed", user.id, exception)
        null
    }

    private fun send(user: LottoUser, message: NotificationMessage, kind: String) {
        try {
            if (sendNotificationPort.send(message) == Delivery.QUEUED) {
                log.warn("[Notify][{}] {} notification queued for resend", user.id, kind)
            } else {
                log.info("[Notify][{}] {} notification sent", user.id, kind)
            }
        } catch (exception: Exception) {
            log.error("[Notify][{}] {} notification failed", user.id, kind, exception)
        }
    }
}
