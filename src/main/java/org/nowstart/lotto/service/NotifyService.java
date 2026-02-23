package org.nowstart.lotto.service;

import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.data.dto.MessageDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyService {

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    private final JavaMailSender javaMailSender;

    @Value("${lotto.mail.from:no-reply@nowstart.org}")
    private String fromAddress;

    public void send(MessageDto message) throws MessagingException {
        try {
            MimeMessageHelper helper = new MimeMessageHelper(javaMailSender.createMimeMessage(), true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(message.getTo());
            helper.setSubject(message.getSubject());
            if (message.getLottoImage() != null) {
                helper.setText(message.getImageText(), true);
                helper.addInline(message.getContentId(), message.getLottoImage());
            } else {
                helper.setText(message.getText(), false);
            }

            javaMailSender.send(helper.getMimeMessage());
            log.info("[Mail] Sent Success - to: {}, subject: {}", message.getTo(), message.getSubject());
        } catch (Exception e) {
            log.error("[Mail] Sent Failed - to: {}, subject: {}", message.getTo(), message.getSubject(), e);
            throw e;
        }
    }
}
