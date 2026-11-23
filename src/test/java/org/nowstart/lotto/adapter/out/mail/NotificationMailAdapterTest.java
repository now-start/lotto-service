package org.nowstart.lotto.adapter.out.mail;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.Address;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nowstart.lotto.application.port.out.SendNotificationPort.Attachment;
import org.nowstart.lotto.application.port.out.SendNotificationPort.Delivery;
import org.nowstart.lotto.application.port.out.SendNotificationPort.NotificationMessage;
import org.nowstart.lotto.domain.exception.NotificationSendException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

@DisplayName("메일 알림 발송")
class NotificationMailAdapterTest {

    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final List<MimeMessage> created = new ArrayList<>();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-22T00:02:00Z"));
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private MailOutbox outbox;
    private NotificationMailAdapter adapter;

    @BeforeEach
    void setUp() {
        given(sender.createMimeMessage()).willAnswer(invocation -> {
            MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
            created.add(mime);
            return mime;
        });
        MailDeliverer deliverer = new MailDeliverer(sender, "sender@example.com", meterRegistry);
        outbox = new MailOutbox(deliverer, clock, meterRegistry, false);
        adapter = new NotificationMailAdapter(deliverer, outbox);
    }

    @Test
    @DisplayName("트레이스 zip을 첨부해 보낸다")
    void shouldSendTraceZipWithFailureText() throws Exception {
        byte[] zip = {1, 2, 3};

        Delivery delivery = adapter.send(message().withAttachment(new Attachment("lotto-trace.zip", zip)));

        then(delivery).isEqualTo(Delivery.SENT);
        MimeMessage mime = created.getFirst();
        mime.saveChanges();
        Multipart parts = (Multipart) mime.getContent();
        var attachment = parts.getBodyPart(parts.getCount() - 1);
        then(attachment.getFileName()).isEqualTo("lotto-trace.zip");
        then(attachment.getContentType()).startsWith("application/zip");
        then(attachment.getInputStream().readAllBytes()).isEqualTo(zip);
        verify(sender).send(mime);
    }

    @Test
    @DisplayName("첨부가 상한을 넘으면 첨부 없이 보내고 본문에 생략 사유를 남긴다")
    void shouldOmitOversizedAttachment() throws Exception {
        byte[] huge = new byte[MailDeliverer.MAX_ATTACHMENT_BYTES + 1];

        adapter.send(message().withAttachment(new Attachment("lotto-trace-big.zip", huge)));

        MimeMessage mime = created.getFirst();
        mime.saveChanges();
        then(bodyText(mime)).contains("[첨부 생략] lotto-trace-big.zip");
        then(attachmentCount(mime)).isZero();
    }

    @Test
    @DisplayName("552(크기 초과)로 거부되면 첨부를 빼고 한 번 더 보낸다 (2026-11-23 유실 사례)")
    void shouldResendWithoutAttachmentWhenRejectedAsTooLarge() throws Exception {
        doThrow(smtpFailure(552, "552 5.3.4 Error: message file too big"))
                .doNothing()
                .when(sender).send(any(MimeMessage.class));

        Delivery delivery = adapter.send(message().withAttachment(new Attachment("lotto-trace.zip", new byte[] {1})));

        then(delivery).isEqualTo(Delivery.SENT);
        verify(sender, times(2)).send(any(MimeMessage.class));
        MimeMessage retried = created.get(1);
        retried.saveChanges();
        then(attachmentCount(retried)).isZero();
        then(bodyText(retried)).contains("[첨부 생략]");
    }

    @Test
    @DisplayName("메일 서버 접속 실패는 대기열에 넣고, 재발송에 성공하면 대기열에서 뺀다 (2026-11-22 유실 사례)")
    void shouldQueueOnConnectionFailureAndResendLater() {
        willThrow(new MailSendException("Mail server connection failed", new SocketTimeoutException("Connect timed out")))
                .given(sender).send(any(MimeMessage.class));

        Delivery delivery = adapter.send(message());

        then(delivery).isEqualTo(Delivery.QUEUED);
        then(outbox.size()).isEqualTo(1);
        then(meterRegistry.get("lotto.notification.outbox.size").gauge().value()).isEqualTo(1.0);

        doNothing().when(sender).send(any(MimeMessage.class));
        clock.advance(MailOutbox.RESEND_INTERVAL);
        outbox.resendDue();

        then(outbox.size()).isZero();
    }

    @Test
    @DisplayName("재발송이 보관 기간을 넘기면 포기하고 실패 메트릭을 남긴다")
    void shouldGiveUpAfterMaxAge() {
        willThrow(new MailSendException("down", new SocketTimeoutException("Connect timed out")))
                .given(sender).send(any(MimeMessage.class));
        adapter.send(message());

        clock.advance(MailOutbox.MAX_AGE.plusMinutes(1));
        outbox.resendDue();

        then(outbox.size()).isZero();
        then(meterRegistry.get("lotto.notification.failed").tag("reason", "expired").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("첨부가 없는데도 크기 초과면 영구 실패로 보고 대기열에 넣지 않는다 (뒤 메일을 막지 않도록)")
    void shouldNotQueueOversizedMessageWithoutAttachment() {
        willThrow(smtpFailure(552, "552 message file too big")).given(sender).send(any(MimeMessage.class));

        thenThrownBy(() -> adapter.send(message())).isInstanceOf(NotificationSendException.class);
        then(outbox.size()).isZero();
    }

    @Test
    @DisplayName("재발송에서 앞의 한 건이 계속 실패해도 뒤 메일은 보낸다")
    void shouldNotLetOneFailingMailBlockTheQueue() throws Exception {
        willThrow(new MailSendException("down", new SocketTimeoutException("Connect timed out")))
                .given(sender).send(any(MimeMessage.class));
        adapter.send(new NotificationMessage("stuck", "a", null, "a@example.com"));
        adapter.send(new NotificationMessage("later", "b", null, "b@example.com"));
        then(outbox.size()).isEqualTo(2);

        org.mockito.Mockito.doAnswer(invocation -> {
            MimeMessage mime = invocation.getArgument(0);
            if ("stuck".equals(mime.getSubject())) {
                throw new MailSendException("still failing", new SocketTimeoutException("timeout"));
            }
            return null;
        }).when(sender).send(any(MimeMessage.class));
        outbox.resendDue();

        then(outbox.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("5xx 영구 실패는 대기열에 넣지 않고 예외로 알린다")
    void shouldThrowOnPermanentFailure() {
        willThrow(smtpFailure(550, "550 mailbox unavailable")).given(sender).send(any(MimeMessage.class));

        thenThrownBy(() -> adapter.send(message())).isInstanceOf(NotificationSendException.class);
        then(outbox.size()).isZero();
    }

    @Test
    @DisplayName("로그용 메일 주소는 앞 두 글자만 남기고 가린다")
    void shouldMaskAddress() {
        then(MailAddresses.mask("someone@example.com")).isEqualTo("so***@example.com");
        then(MailAddresses.mask("a@example.com")).isEqualTo("a***@example.com");
        then(MailAddresses.mask(null)).isEqualTo("<none>");
    }

    private NotificationMessage message() {
        return new NotificationMessage("failure", "error details", null, "user@example.com");
    }

    private MailSendException smtpFailure(int code, String text) {
        SMTPSendFailedException smtp = new SMTPSendFailedException("DATA", code, text, null,
                new Address[0], new Address[0], new Address[0]);
        return new MailSendException(Map.of(new Object(), smtp));
    }

    private int attachmentCount(MimeMessage mime) throws Exception {
        Multipart parts = (Multipart) mime.getContent();
        int count = 0;
        for (int index = 0; index < parts.getCount(); index++) {
            if (parts.getBodyPart(index).getFileName() != null) {
                count++;
            }
        }
        return count;
    }

    private String bodyText(MimeMessage mime) throws Exception {
        Multipart parts = (Multipart) mime.getContent();
        Object first = parts.getBodyPart(0).getContent();
        if (first instanceof Multipart nested) {
            return String.valueOf(nested.getBodyPart(0).getContent());
        }
        return String.valueOf(first);
    }

    static final class MutableClock extends Clock {

        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Seoul");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
