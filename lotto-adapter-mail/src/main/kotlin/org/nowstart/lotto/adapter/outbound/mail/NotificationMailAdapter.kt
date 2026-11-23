package org.nowstart.lotto.adapter.outbound.mail

import org.nowstart.lotto.application.port.outbound.Delivery
import org.nowstart.lotto.application.port.outbound.NotificationMessage
import org.nowstart.lotto.application.port.outbound.SendNotificationPort
import org.nowstart.lotto.domain.exception.NotificationSendException
import org.springframework.stereotype.Component

/**
 * 메일 알림 어댑터. 일시 장애는 재발송 대기열로 넘겨 알림이 사라지지 않게 하고,
 * 다시 보내도 소용없는 영구 실패만 예외로 알린다.
 */
@Component
class NotificationMailAdapter(
    private val mailDeliverer: MailDeliverer,
    private val mailOutbox: MailOutbox,
) : SendNotificationPort {

    override fun send(message: NotificationMessage): Delivery {
        val result = mailDeliverer.deliver(message)
        if (result.isSent) {
            return Delivery.SENT
        }
        if (result.kind == SmtpFailureKind.PERMANENT) {
            throw NotificationSendException("메일 발송에 실패했습니다.", result.failure)
        }
        mailOutbox.enqueue(result.message)
        return Delivery.QUEUED
    }
}
