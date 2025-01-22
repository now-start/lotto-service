package org.nowstart.lotto.service.notify;

import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleNotifyService implements NotifyService{

    @Value("${lotto.email}")
    private String email;
    private final JavaMailSender javaMailSender;

    @Override
    public void send(String message) throws MessagingException {
        MimeMessageHelper helper = new MimeMessageHelper(javaMailSender.createMimeMessage(), "UTF-8");

        helper.setFrom("sender@example.com");
        helper.setTo("phantom2691@naver.com");
        helper.setSubject("테스트");
        helper.setText(message, true);

        javaMailSender.send(helper.getMimeMessage());
    }
}
