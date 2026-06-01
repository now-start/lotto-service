package org.nowstart.lotto.application.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.CheckResult;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.LottoAccountSnapshot;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.LottoResult;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort.LottoUser;
import org.nowstart.lotto.application.port.out.SendNotificationPort.NotificationMessage;
import org.nowstart.lotto.domain.type.MessageType;
import org.nowstart.lotto.domain.type.TaskMode;

public class LottoNotificationFactory {

    public Optional<NotificationMessage> createCheckSuccessMessage(
            LottoUser user,
            LottoAccountSnapshot accountSnapshot,
            List<CheckResult> results
    ) {
        if (results.isEmpty()) {
            return Optional.empty();
        }

        CheckResult latestCheckResult = results.stream()
                .max(Comparator.comparingInt(this::roundNumber))
                .orElseThrow();
        var latestResult = latestCheckResult.result();
        return Optional.of(new NotificationMessage(
                "[" + user.id() + "] " + summary(latestResult),
                createResultMessageText(accountSnapshot, latestResult),
                latestCheckResult.detailImage(),
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
                accountSnapshotText(accountSnapshot),
                null,
                user.email()
        );
    }

    private String createResultMessageText(LottoAccountSnapshot accountSnapshot, LottoResult result) {
        return """
                %s
                
                일자: %s
                회차: %s
                게임: %s
                번호: %s
                수량: %s
                결과: %s
                금액: %s
                """.formatted(
                accountSnapshotText(accountSnapshot),
                result.date(),
                result.round(),
                result.name(),
                result.number(),
                result.count(),
                result.result(),
                result.price()
        );
    }

    private String summary(LottoResult result) {
        MessageType messageType = MessageType.of(result.result());
        return messageType.getEmoji() + result.name() + " " + result.round() + "회차" + messageType.getEmoji();
    }

    private String accountSnapshotText(LottoAccountSnapshot accountSnapshot) {
        return accountSnapshot.name() + "의 💰예치금 : " + accountSnapshot.deposit();
    }

    private int roundNumber(CheckResult checkResult) {
        return Integer.parseInt(checkResult.result().round());
    }
}
