package org.nowstart.lotto.adapter.out.mail;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.application.port.out.SendNotificationPort.NotificationMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * 메일 1건을 실제로 보낸다. 크기 초과(552)면 첨부를 빼고 한 번 더 보낸다.
 * 결과는 재발송 판단에 쓰도록 {@link Result}로 돌려주고 예외를 던지지 않는다.
 */
@Slf4j
@Component
class MailDeliverer {

    /** 대부분의 SMTP 서버 한도(10~25MB)와 base64 팽창(약 1.37배)을 고려한 첨부 상한. */
    static final int MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024;

    private static final String CONTENT_ID = "lottoImage";
    private static final String INLINE_IMAGE_CONTENT_TYPE = "image/png";

    private final JavaMailSender javaMailSender;
    private final String fromAddress;
    private final MeterRegistry meterRegistry;

    MailDeliverer(
            JavaMailSender javaMailSender,
            @Value("${lotto.mail.from:no-reply@nowstart.org}") String fromAddress,
            MeterRegistry meterRegistry
    ) {
        this.javaMailSender = javaMailSender;
        this.fromAddress = fromAddress;
        this.meterRegistry = meterRegistry;
    }

    Result deliver(NotificationMessage message) {
        NotificationMessage sized = limitAttachment(message);
        Result result = sendOnce(sized);
        if (result.kind() == SmtpFailureKind.MESSAGE_TOO_LARGE && sized.attachment() != null) {
            log.warn("[Mail] Message too large - resending without attachment to: {}", MailAddresses.mask(sized.to()));
            result = sendOnce(sized.withoutAttachment("메일 서버가 크기 초과로 거부해 첨부를 뺐습니다."));
        }
        if (result.kind() == SmtpFailureKind.MESSAGE_TOO_LARGE) {
            // 첨부를 빼고도 크면 다시 보내도 같은 결과다(대기열에 넣으면 뒤 메일까지 막는다).
            return Result.failed(result.message(), SmtpFailureKind.PERMANENT, result.failure());
        }
        return result;
    }

    private NotificationMessage limitAttachment(NotificationMessage message) {
        if (message.attachment() == null || message.attachment().size() <= MAX_ATTACHMENT_BYTES) {
            return message;
        }
        log.warn("[Mail] Attachment {} ({} bytes) exceeds {} bytes - omitted",
                message.attachment().filename(), message.attachment().size(), MAX_ATTACHMENT_BYTES);
        return message.withoutAttachment("첨부 상한(" + MAX_ATTACHMENT_BYTES + " bytes)을 넘어 생략했습니다. 서버 logs 디렉터리에 보관됩니다.");
    }

    private Result sendOnce(NotificationMessage message) {
        try {
            MimeMessageHelper helper = new MimeMessageHelper(javaMailSender.createMimeMessage(), true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(message.to());
            helper.setSubject(message.subject());

            if (message.hasInlineImage()) {
                String htmlBody = HtmlUtils.htmlEscape(message.text()).replace("\n", "<br/>")
                        + "<br/><br/><img src='cid:" + CONTENT_ID + "'/>";
                helper.setText(htmlBody, true);
                helper.addInline(CONTENT_ID, new ByteArrayResource(message.inlineImage()), INLINE_IMAGE_CONTENT_TYPE);
            } else {
                helper.setText(message.text(), false);
            }

            if (message.attachment() != null) {
                helper.addAttachment(message.attachment().filename(),
                        new ByteArrayResource(message.attachment().content()), message.attachment().contentType());
            }

            javaMailSender.send(helper.getMimeMessage());
            log.info("[Mail] Sent Success - to: {}, subject: {}", MailAddresses.mask(message.to()), message.subject());
            return Result.sent(message);
        } catch (Exception exception) {
            SmtpFailureKind kind = SmtpFailureKind.classify(exception);
            meterRegistry.counter("lotto.notification.failed", "reason", kind.name().toLowerCase()).increment();
            log.error("[Mail] Sent Failed - to: {}, subject: {}, kind={}, cause={}",
                    MailAddresses.mask(message.to()), message.subject(), kind, exception.getMessage(), exception);
            return Result.failed(message, kind, exception);
        }
    }

    /**
     * @param message 실제로 보냈거나 보내려던 메시지(첨부가 빠졌을 수 있음)
     * @param kind    실패 분류, 성공이면 null
     */
    record Result(NotificationMessage message, SmtpFailureKind kind, Exception failure) {

        static Result sent(NotificationMessage message) {
            return new Result(message, null, null);
        }

        static Result failed(NotificationMessage message, SmtpFailureKind kind, Exception failure) {
            return new Result(message, kind, failure);
        }

        boolean isSent() {
            return kind == null;
        }
    }
}
