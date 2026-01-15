package org.nowstart.lotto.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.data.dto.MessageDto;
import org.nowstart.lotto.data.dto.ResultDto;
import org.nowstart.lotto.data.dto.TaskResult;
import org.nowstart.lotto.data.entity.UserEntity;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskWriter implements ItemWriter<TaskResult> {

    private final JavaMailSender javaMailSender;

    @Override
    public void write(Chunk<? extends TaskResult> chunk) {
        for (TaskResult result : chunk) {
            log.info("[Writer][{}] - Task Processed Successfully", result.getUser().getId());
            sendSuccessNotification(result);
        }
    }

    private void sendSuccessNotification(TaskResult result) {
        UserEntity user = result.getUser();
        if (result.getResults().isEmpty()) {
            log.info("[Writer][{}] - No results to notify", user.getId());
            return;
        }

        ResultDto latestResult = result.getResults().getFirst();
        try {
            sendEmail(MessageDto.builder()
                    .subject(String.format("[%s] %s", user.getId(), latestResult.toString()))
                    .text(String.format("사용자: %s\n이메일: %s", user.getId(), user.getEmail()))
                    .image(latestResult.getImage())
                    .to(user.getEmail())
                    .build());
            log.info("[Writer][{}] - Success Notification Sent", user.getId());
        } catch (Exception e) {
            log.error("[Writer][{}] - Success Notification Failed", user.getId(), e);
        }
    }

    private void sendEmail(MessageDto message) throws Exception {
        MimeMessageHelper helper = new MimeMessageHelper(javaMailSender.createMimeMessage(), true, "UTF-8");
        helper.setFrom("no-reply@nowstart.org");
        helper.setTo(message.getTo());
        helper.setSubject(message.getSubject());
        if (message.getImage() != null) {
            helper.setText(message.getImageText(), true);
            helper.addInline(message.getContentId(), message.getImage());
        } else {
            helper.setText(message.getText());
        }
        javaMailSender.send(helper.getMimeMessage());
    }

}
