package org.nowstart.lotto.service;

import jakarta.mail.MessagingException;
import java.io.UnsupportedEncodingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.data.dto.MessageDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleNotifyService {

    @Value("${spring.mail.username}")
    private String fromEmail;
    @Value("${lotto.email}")
    private String toEmail;

    /**
     * 이매일 전송
     *
     * @param message message
     * @throws MessagingException MessagingException
     * @throws UnsupportedEncodingException UnsupportedEncodingException
     */
    public void send(MessageDto message) throws MessagingException, UnsupportedEncodingException {
        JavaMailSender javaMailSender = new JavaMailSenderImpl();
        MimeMessageHelper helper = new MimeMessageHelper(javaMailSender.createMimeMessage(), true, "UTF-8");

        helper.setFrom(fromEmail, "Lotto");
        helper.setTo(toEmail);
        helper.setSubject(message.getSubject());
        helper.setText(message.getText());
        if (message.getLottoImage() != null) {
            helper.addInline(message.getContentId(), message.getLottoImage());
            helper.setText(message.getImageText(), true);
        }

        javaMailSender.send(helper.getMimeMessage());
    }
}
