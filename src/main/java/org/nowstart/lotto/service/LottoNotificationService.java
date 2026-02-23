package org.nowstart.lotto.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.data.dto.LottoResultDto;
import org.nowstart.lotto.data.dto.LottoUserDto;
import org.nowstart.lotto.data.dto.MessageDto;
import org.nowstart.lotto.data.properties.LottoProperties;
import org.nowstart.lotto.data.type.TaskMode;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LottoNotificationService {

    private final NotifyService notifyService;

    public void sendSuccess(LottoProperties.User user, LottoUserDto lottoUserDto, List<LottoResultDto> results) {
        if (results.isEmpty()) {
            log.info("[Notify][{}] Skip success notification because there are no lotto results", user.getId());
            return;
        }

        LottoResultDto latestResult = results.getFirst();
        MessageDto messageDto = MessageDto.builder()
                .subject(String.format("[%s] %s", user.getId(), latestResult))
                .text(lottoUserDto.toString())
                .lottoImage(latestResult.getLottoImage())
                .to(user.getEmail())
                .build();

        try {
            notifyService.send(messageDto);
            log.info("[Notify][{}] Success notification sent", user.getId());
        } catch (Exception e) {
            log.error("[Notify][{}] Success notification failed", user.getId(), e);
        }
    }

    public void sendFailure(LottoProperties.User user, TaskMode mode, Exception exception) {
        String failureMessage = String.format("""
                        [사용자: %s]
                        작업 실행 중 오류가 발생했습니다.
                        
                        오류 유형: %s
                        오류 메시지: %s
                        
                        자세한 내용은 서버 로그를 확인해 주세요.""",
                user.getId(), exception.getClass().getSimpleName(), exception.getMessage());

        MessageDto messageDto = MessageDto.builder()
                .subject(String.format("[%s] %s", user.getId(), mode.getFailureSubject()))
                .text(failureMessage)
                .to(user.getEmail())
                .build();

        try {
            notifyService.send(messageDto);
            log.info("[Notify][{}] Failure notification sent", user.getId());
        } catch (Exception notificationError) {
            log.error("[Notify][{}] Failure notification failed", user.getId(), notificationError);
        }
    }
}
