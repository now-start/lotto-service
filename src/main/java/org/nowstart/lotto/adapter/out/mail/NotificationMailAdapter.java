package org.nowstart.lotto.adapter.out.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.application.port.out.SendNotificationPort;
import org.nowstart.lotto.domain.exception.NotificationSendException;
import org.nowstart.lotto.application.port.out.SendNotificationPort.NotificationMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationMailAdapter implements SendNotificationPort {

    private static final String CONTENT_ID = "lottoImage";
    private static final String INLINE_IMAGE_CONTENT_TYPE = "image/png";

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    private final JavaMailSender javaMailSender;

    @Value("${lotto.mail.from:no-reply@nowstart.org}")
    private String fromAddress;

    @Override
    public void send(NotificationMessage message) {
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

            javaMailSender.send(helper.getMimeMessage());
            log.info("[Mail] Sent Success - to: {}, subject: {}", message.to(), message.subject());
        } catch (Exception exception) {
            log.error("[Mail] Sent Failed - to: {}, subject: {}, cause={}",
                    message.to(), message.subject(), exception.getMessage(), exception);
            throw new NotificationSendException("메일 발송에 실패했습니다.", exception);
        }
    }
}
