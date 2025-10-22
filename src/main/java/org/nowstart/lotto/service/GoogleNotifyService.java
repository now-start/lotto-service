package org.nowstart.lotto.service;

import jakarta.mail.MessagingException;
import java.io.UnsupportedEncodingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.data.dto.MessageDto;
import org.nowstart.lotto.data.properties.LottoProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleNotifyService {

    private final LottoProperties lottoProperties;
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    private final JavaMailSender javaMailSender;

    public void send(MessageDto message) throws MessagingException, UnsupportedEncodingException {
        try {
            MimeMessageHelper helper = new MimeMessageHelper(javaMailSender.createMimeMessage(), true, "UTF-8");

            helper.setFrom("no-reply@nowstart.org");
            helper.setTo(lottoProperties.getEmail());
            helper.setSubject(message.getSubject());
            helper.setText(message.getText());
            if (message.getLottoImage() != null) {
                helper.addInline(message.getContentId(), message.getLottoImage());
                helper.setText(message.getImageText(), true);
            }

            javaMailSender.send(helper.getMimeMessage());
            log.info("메일 발송 완료 - 제목: {}", message.getSubject());
        } catch (Exception e) {
            if (e.getMessage().contains("Daily user sending limit exceeded") ||
                    e.getMessage().contains("550")) {
                log.warn("Gmail 일일 발송 제한 초과 - 메일 발송 스킵: {}", message.getSubject());
            } else {
                log.error("메일 발송 실패 - 제목: {}, 오류: {}", message.getSubject(), e.getMessage());
                throw e;
            }
        }
    }
}
