package org.nowstart.lotto.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.data.dto.MessageDto;
import org.nowstart.lotto.data.dto.TaskResult;
import org.nowstart.lotto.data.entity.UserEntity;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@StepScope
@RequiredArgsConstructor
public class TaskSkipListener implements SkipListener<UserEntity, TaskResult> {

    private final JavaMailSender javaMailSender;

    @Value("#{jobParameters['failureSubject']}")
    private String failureSubject;

    @Override
    public void onSkipInProcess(UserEntity user, Throwable t) {
        log.error("[SkipListener][{}] - Item skipped after retries. Error: {}", user.getId(), t.getMessage());
        sendFailureNotification(user, t.getMessage());
    }

    @Override
    public void onSkipInRead(Throwable t) {
        log.error("[SkipListener] - Item skipped in read. Error: {}", t.getMessage());
    }

    @Override
    public void onSkipInWrite(TaskResult item, Throwable t) {
        log.error("[SkipListener][{}] - Item skipped in write. Error: {}", item.getUser().getId(), t.getMessage());
    }

    private void sendFailureNotification(UserEntity user, String errorMessage) {
        try {
            String failureMessage = String.format("""
                            [사용자: %s]
                            작업 실행 중 오류가 발생했습니다. (모든 재시도 실패)
                            
                            오류 메시지: %s
                            
                            자세한 내용은 서버 로그를 확인해 주세요.""",
                    user.getId(), errorMessage);

            sendEmail(MessageDto.builder()
                    .subject(String.format("[%s] %s", user.getId(), failureSubject))
                    .text(failureMessage)
                    .to(user.getEmail())
                    .build());
            log.info("[SkipListener][{}] - Failure Notification Sent", user.getId());
        } catch (Exception e) {
            log.error("[SkipListener][{}] - Failure Notification Failed", user.getId(), e);
        }
    }

    private void sendEmail(MessageDto message) throws Exception {
        MimeMessageHelper helper = new MimeMessageHelper(javaMailSender.createMimeMessage(), true, "UTF-8");
        helper.setFrom("no-reply@nowstart.org");
        helper.setTo(message.getTo());
        helper.setSubject(message.getSubject());
        helper.setText(message.getText());
        javaMailSender.send(helper.getMimeMessage());
    }
}
