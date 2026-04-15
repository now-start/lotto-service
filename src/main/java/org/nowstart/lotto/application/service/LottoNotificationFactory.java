package org.nowstart.lotto.application.service;

import java.util.List;
import java.util.Optional;
import org.nowstart.lotto.domain.model.LottoAccountSnapshot;
import org.nowstart.lotto.domain.model.LottoResult;
import org.nowstart.lotto.domain.model.LottoUser;
import org.nowstart.lotto.domain.model.NotificationMessage;
import org.nowstart.lotto.domain.type.TaskMode;

public class LottoNotificationFactory {

    public Optional<NotificationMessage> createSuccessMessage(
            LottoUser user,
            LottoAccountSnapshot accountSnapshot,
            List<LottoResult> results
    ) {
        if (results.isEmpty()) {
            return Optional.empty();
        }

        LottoResult latestResult = results.getFirst();
        return Optional.of(new NotificationMessage(
                "[" + user.id() + "] " + latestResult.summary(),
                accountSnapshot.asNotificationText(),
                latestResult.imageBytes(),
                user.email()
        ));
    }

    public NotificationMessage createFailureMessage(LottoUser user, TaskMode mode, Exception exception) {
        String failureMessage = String.format("""
                        [사용자: %s]
                        작업 실행 중 오류가 발생했습니다.
                        
                        오류 유형: %s
                        오류 메시지: %s
                        
                        자세한 내용은 서버 로그를 확인해 주세요.""",
                user.id(),
                exception.getClass().getSimpleName(),
                exception.getMessage()
        );

        return new NotificationMessage(
                "[" + user.id() + "] " + mode.getFailureSubject(),
                failureMessage,
                null,
                user.email()
        );
    }

    public NotificationMessage createInitializationMessage(LottoUser user, LottoAccountSnapshot accountSnapshot) {
        return new NotificationMessage(
                "⏳[" + user.id() + "] Lotto Init Test⏳",
                accountSnapshot.asNotificationText(),
                null,
                user.email()
        );
    }
}
