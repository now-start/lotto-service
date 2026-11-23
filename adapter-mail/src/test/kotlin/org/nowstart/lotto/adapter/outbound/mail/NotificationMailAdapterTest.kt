package org.nowstart.lotto.adapter.outbound.mail

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import jakarta.mail.Address
import jakarta.mail.Multipart
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import java.net.SocketTimeoutException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Properties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.angus.mail.smtp.SMTPSendFailedException
import org.junit.jupiter.api.Test
import org.nowstart.lotto.application.port.outbound.Attachment
import org.nowstart.lotto.application.port.outbound.Delivery
import org.nowstart.lotto.application.port.outbound.NotificationMessage
import org.nowstart.lotto.domain.exception.NotificationSendException
import org.springframework.mail.MailSendException
import org.springframework.mail.javamail.JavaMailSender

class NotificationMailAdapterTest {

    private val sender = mockk<JavaMailSender>()
    private val created = mutableListOf<MimeMessage>()
    private val clock = MutableClock(Instant.parse("2026-11-22T00:02:00Z"))
    private val meterRegistry = SimpleMeterRegistry()
    private val deliverer = MailDeliverer(sender, "sender@example.com", meterRegistry)
    private val outbox = MailOutbox(deliverer, clock, meterRegistry, startScheduler = false)
    private val adapter = NotificationMailAdapter(deliverer, outbox)

    init {
        every { sender.createMimeMessage() } answers { MimeMessage(Session.getInstance(Properties())).also { created += it } }
        every { sender.send(any<MimeMessage>()) } just runs
    }

    @Test
    fun `트레이스 zip을 첨부해 보낸다`() {
        val zip = byteArrayOf(1, 2, 3)

        assertThat(adapter.send(message().withAttachment(Attachment("lotto-trace.zip", zip)))).isEqualTo(Delivery.SENT)

        val parts = created.first().apply { saveChanges() }.content as Multipart
        val attachment = parts.getBodyPart(parts.count - 1)
        assertThat(attachment.fileName).isEqualTo("lotto-trace.zip")
        assertThat(attachment.contentType).startsWith("application/zip")
        assertThat(attachment.inputStream.readAllBytes()).isEqualTo(zip)
    }

    @Test
    fun `첨부가 상한을 넘으면 첨부 없이 보내고 본문에 생략 사유를 남긴다`() {
        adapter.send(message().withAttachment(Attachment("lotto-trace-big.zip", ByteArray(MailDeliverer.MAX_ATTACHMENT_BYTES + 1))))

        val mime = created.first().apply { saveChanges() }
        assertThat(bodyText(mime)).contains("[첨부 생략] lotto-trace-big.zip")
        assertThat(attachmentCount(mime)).isZero()
    }

    @Test
    fun `552로 거부되면 첨부를 빼고 한 번 더 보낸다 (2026-11-23 유실 사례)`() {
        every { sender.send(any<MimeMessage>()) } throws smtpFailure(552) andThen Unit

        assertThat(adapter.send(message().withAttachment(Attachment("lotto-trace.zip", byteArrayOf(1))))).isEqualTo(Delivery.SENT)

        verify(exactly = 2) { sender.send(any<MimeMessage>()) }
        val retried = created[1].apply { saveChanges() }
        assertThat(attachmentCount(retried)).isZero()
        assertThat(bodyText(retried)).contains("[첨부 생략]")
    }

    @Test
    fun `첨부가 없는데도 크기 초과면 영구 실패로 보고 대기열에 넣지 않는다`() {
        every { sender.send(any<MimeMessage>()) } throws smtpFailure(552)

        assertThatThrownBy { adapter.send(message()) }.isInstanceOf(NotificationSendException::class.java)
        assertThat(outbox.size()).isZero()
    }

    @Test
    fun `메일 서버 접속 실패는 대기열에 넣고 재발송에 성공하면 대기열에서 뺀다 (2026-11-22 유실 사례)`() {
        every { sender.send(any<MimeMessage>()) } throws connectionFailure()

        assertThat(adapter.send(message())).isEqualTo(Delivery.QUEUED)
        assertThat(outbox.size()).isEqualTo(1)
        assertThat(meterRegistry.get("lotto.notification.outbox.size").gauge().value()).isEqualTo(1.0)

        every { sender.send(any<MimeMessage>()) } just runs
        clock.advance(MailOutbox.RESEND_INTERVAL)
        outbox.resendDue()

        assertThat(outbox.size()).isZero()
    }

    @Test
    fun `재발송이 보관 기간을 넘기면 포기하고 실패 메트릭을 남긴다`() {
        every { sender.send(any<MimeMessage>()) } throws connectionFailure()
        adapter.send(message())

        clock.advance(MailOutbox.MAX_AGE.plusMinutes(1))
        outbox.resendDue()

        assertThat(outbox.size()).isZero()
        assertThat(meterRegistry.get("lotto.notification.failed").tag("reason", "expired").counter().count()).isEqualTo(1.0)
    }

    @Test
    fun `재발송에서 앞의 한 건이 계속 실패해도 뒤 메일은 보낸다`() {
        every { sender.send(any<MimeMessage>()) } throws connectionFailure()
        adapter.send(NotificationMessage("stuck", "a", null, "a@example.com"))
        adapter.send(NotificationMessage("later", "b", null, "b@example.com"))

        every { sender.send(any<MimeMessage>()) } answers {
            if (firstArg<MimeMessage>().subject == "stuck") throw connectionFailure()
        }
        outbox.resendDue()

        assertThat(outbox.size()).isEqualTo(1)
    }

    @Test
    fun `5xx 영구 실패는 대기열에 넣지 않고 예외로 알린다`() {
        every { sender.send(any<MimeMessage>()) } throws smtpFailure(550)

        assertThatThrownBy { adapter.send(message()) }.isInstanceOf(NotificationSendException::class.java)
        assertThat(outbox.size()).isZero()
    }

    @Test
    fun `로그용 메일 주소는 앞 두 글자만 남기고 가린다`() {
        assertThat(maskAddress("someone@example.com")).isEqualTo("so***@example.com")
        assertThat(maskAddress("a@example.com")).isEqualTo("a***@example.com")
        assertThat(maskAddress(null)).isEqualTo("<none>")
    }

    private fun message() = NotificationMessage("failure", "error details", null, "user@example.com")

    private fun connectionFailure() = MailSendException("Mail server connection failed", SocketTimeoutException("Connect timed out"))

    private fun smtpFailure(code: Int) = MailSendException(
        mapOf<Any, Exception>(Any() to SMTPSendFailedException("DATA", code, "$code error", null, arrayOf<Address>(), arrayOf(), arrayOf())),
    )

    private fun attachmentCount(mime: MimeMessage): Int {
        val parts = mime.content as Multipart
        return (0 until parts.count).count { parts.getBodyPart(it).fileName != null }
    }

    private fun bodyText(mime: MimeMessage): String {
        val first = (mime.content as Multipart).getBodyPart(0).content
        return if (first is Multipart) first.getBodyPart(0).content.toString() else first.toString()
    }

    private class MutableClock(private var now: Instant) : Clock() {
        fun advance(duration: Duration) {
            now = now.plus(duration)
        }

        override fun getZone(): ZoneId = ZoneId.of("Asia/Seoul")

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = now
    }
}
