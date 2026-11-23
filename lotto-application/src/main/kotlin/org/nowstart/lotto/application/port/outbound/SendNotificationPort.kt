package org.nowstart.lotto.application.port.outbound

fun interface SendNotificationPort {

    /**
     * 알림을 보낸다. 일시 장애로 즉시 보내지 못하면 재발송 대기열에 넣고 [Delivery.QUEUED]를 돌려준다.
     * 재발송으로도 해결되지 않는 영구 실패만 예외로 던진다.
     */
    fun send(message: NotificationMessage): Delivery
}

enum class Delivery {
    SENT,
    QUEUED,
}

class NotificationMessage(
    val subject: String,
    val text: String,
    inlineImage: ByteArray?,
    val to: String,
    val attachment: Attachment? = null,
) {
    private val image: ByteArray? = inlineImage?.copyOf()

    val inlineImage: ByteArray?
        get() = image?.copyOf()

    val hasInlineImage: Boolean
        get() = image != null && image.isNotEmpty()

    fun withAttachment(attachment: Attachment?): NotificationMessage =
        NotificationMessage(subject, text, image, to, attachment)

    /** 첨부를 빼고, 뺀 이유를 본문 끝에 덧붙인다. */
    fun withoutAttachment(reason: String): NotificationMessage {
        val omitted = attachment ?: return this
        val note = "\n\n[첨부 생략] ${omitted.filename} (${omitted.size} bytes): $reason"
        return NotificationMessage(subject, text + note, image, to, null)
    }
}

class Attachment(val filename: String, content: ByteArray, val contentType: String = ZIP) {

    private val bytes: ByteArray = content.copyOf()

    val content: ByteArray
        get() = bytes.copyOf()

    val size: Int
        get() = bytes.size

    companion object {
        const val ZIP = "application/zip"
        const val PNG = "image/png"
    }
}
