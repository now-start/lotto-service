package org.nowstart.lotto.service;

import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.data.dto.MessageDto;
import org.nowstart.lotto.data.properties.LottoProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleNotifyService {

    private final LottoProperties lottoProperties;
    private final JavaMailSender javaMailSender;

    /**
     * 이매일 전송
     *
     * @param message message
     * @throws MessagingException MessagingException
     * @throws UnsupportedEncodingException UnsupportedEncodingException
     */
    public void send(MessageDto message) throws MessagingException, UnsupportedEncodingException {
        MimeMessageHelper helper = new MimeMessageHelper(javaMailSender.createMimeMessage(), true, "UTF-8");

        helper.setFrom(lottoProperties.getFromEmail(), "Lotto");
        helper.setTo(lottoProperties.getEmail());
        helper.setSubject(message.getSubject());
        helper.setText(message.getText());
        if (message.getLottoImage() != null) {
            helper.addInline(message.getContentId(), message.getLottoImage());
            helper.setText(message.getImageText(), true);
        }

        javaMailSender.send(helper.getMimeMessage());
    }
}
