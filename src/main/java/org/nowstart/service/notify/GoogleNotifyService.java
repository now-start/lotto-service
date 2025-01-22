package org.nowstart.service.notify;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleNotifyService implements NotifyService{

    @Value("${lotto.email}")
    private final String email;
    private final JavaMailSender javaMailSender;

    @Override
    public void send(String message) {
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setSubject("Google Notify");
        mailMessage.setText(message);
        mailMessage.setTo(email);
        javaMailSender.send(mailMessage);
    }
}
