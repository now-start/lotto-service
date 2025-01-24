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
public class GoogleNotifyService {

    @Value("${lotto.email}")
    private String email;
    public static final String CONTENT_ID = "lottoImage";
    public static final String IMAGE_CONTENT = "<img src='cid:lottoImage'/>";
    private final JavaMailSender javaMailSender;

    /**
     * Send email
     *
     * @param message
     * @throws MessagingException
     */
    public void send(MessageDto message) throws MessagingException {
        MimeMessageHelper helper = new MimeMessageHelper(javaMailSender.createMimeMessage(), true, "UTF-8");

        helper.setTo(email);
        helper.setSubject(message.getSubject());
        helper.setText(message.getText());
        if (message.getLottoImage() != null) {
            helper.addInline(CONTENT_ID, message.getLottoImage());
            helper.setText(IMAGE_CONTENT, true);
        }

        javaMailSender.send(helper.getMimeMessage());
    }
}
