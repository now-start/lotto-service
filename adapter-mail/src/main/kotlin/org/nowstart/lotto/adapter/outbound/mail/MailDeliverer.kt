package org.nowstart.lotto.adapter.outbound.mail

import io.micrometer.core.instrument.MeterRegistry
import org.nowstart.lotto.application.port.outbound.NotificationMessage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component

private val log = LoggerFactory.getLogger(MailDeliverer::class.java)

/**
 * 메일 1건을 실제로 보낸다. 크기 초과(552)면 첨부를 빼고 한 번 더 보낸다.
 * 결과는 재발송 판단에 쓰도록 [Result]로 돌려주고 예외를 던지지 않는다.
 */
@Component
class MailDeliverer(
    private val javaMailSender: JavaMailSender,
    @Value("\${lotto.mail.from:no-reply@nowstart.org}") private val fromAddress: String,
    private val meterRegistry: MeterRegistry,
) {

    fun deliver(message: NotificationMessage): Result {
        val sized = limitAttachment(message)
        var result = sendOnce(sized)
        if (result.kind == SmtpFailureKind.MESSAGE_TOO_LARGE && sized.attachment != null) {
            log.warn("[Mail] Message too large - resending without attachment to: {}", maskAddress(sized.to))
            result = sendOnce(sized.withoutAttachment("메일 서버가 크기 초과로 거부해 첨부를 뺐습니다."))
        }
        if (result.kind == SmtpFailureKind.MESSAGE_TOO_LARGE) {
            // 첨부를 빼고도 크면 다시 보내도 같은 결과다(대기열에 넣으면 뒤 메일까지 막는다).
            return result.copy(kind = SmtpFailureKind.PERMANENT)
        }
        return result
    }

    private fun limitAttachment(message: NotificationMessage): NotificationMessage {
        val attachment = message.attachment ?: return message
        if (attachment.size <= MAX_ATTACHMENT_BYTES) {
            return message
        }
        log.warn("[Mail] Attachment {} ({} bytes) exceeds {} bytes - omitted", attachment.filename, attachment.size, MAX_ATTACHMENT_BYTES)
        return message.withoutAttachment("첨부 상한(${MAX_ATTACHMENT_BYTES} bytes)을 넘어 생략했습니다. 서버 logs 디렉터리에 보관됩니다.")
    }

    private fun sendOnce(message: NotificationMessage): Result = try {
        val helper = MimeMessageHelper(javaMailSender.createMimeMessage(), true, "UTF-8")
        helper.setFrom(fromAddress)
        helper.setTo(message.to)
        helper.setSubject(message.subject)
        val image = message.inlineImage
        if (image != null && image.isNotEmpty()) {
            helper.setText(escapeHtml(message.text).replace("\n", "<br/>") + "<br/><br/><img src='cid:$CONTENT_ID'/>", true)
            helper.addInline(CONTENT_ID, ByteArrayResource(image), INLINE_IMAGE_CONTENT_TYPE)
        } else {
            helper.setText(message.text, false)
        }
        message.attachment?.let { helper.addAttachment(it.filename, ByteArrayResource(it.content), it.contentType) }

        javaMailSender.send(helper.mimeMessage)
        log.info("[Mail] Sent Success - to: {}, subject: {}", maskAddress(message.to), message.subject)
        Result(message, null, null)
    } catch (exception: Exception) {
        val kind = SmtpFailureKind.classify(exception)
        meterRegistry.counter("lotto.notification.failed", "reason", kind.name.lowercase()).increment()
        log.error("[Mail] Sent Failed - to: {}, subject: {}, kind={}, cause={}",
            maskAddress(message.to), message.subject, kind, exception.message, exception)
        Result(message, kind, exception)
    }

    /**
     * @property message 실제로 보냈거나 보내려던 메시지(첨부가 빠졌을 수 있음)
     * @property kind 실패 분류, 성공이면 null
     */
    data class Result(val message: NotificationMessage, val kind: SmtpFailureKind?, val failure: Exception?) {
        val isSent: Boolean get() = kind == null
    }

    companion object {
        /** 대부분의 SMTP 서버 한도(10~25MB)와 base64 팽창(약 1.37배)을 고려한 첨부 상한. */
        const val MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024

        private const val CONTENT_ID = "lottoImage"
        private const val INLINE_IMAGE_CONTENT_TYPE = "image/png"
    }
}
