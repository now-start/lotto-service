package org.nowstart.lotto.application.service;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.application.port.out.LottoAutomationPort;
import org.nowstart.lotto.application.port.out.LottoAutomationSession;
import org.nowstart.lotto.application.port.out.SendNotificationPort;
import org.nowstart.lotto.domain.exception.LottoAutomationException;
import org.nowstart.lotto.domain.model.LottoExecution;
import org.nowstart.lotto.domain.model.LottoUser;
import org.nowstart.lotto.domain.model.NotificationMessage;
import org.nowstart.lotto.domain.type.ExecutionStatus;
import org.nowstart.lotto.domain.type.TaskMode;
import org.nowstart.lotto.domain.type.TriggerType;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LottoExecutionRunner {

    private final LottoUserResolver lottoUserResolver;
    private final LottoAutomationPort lottoAutomationPort;
    private final SendNotificationPort sendNotificationPort;
    private final LottoNotificationFactory lottoNotificationFactory;

    public LottoExecution run(
            TaskMode mode,
            TriggerType trigger,
            List<String> requestedUserIds,
            LottoUserExecutionFlow executionFlow
    ) {
        List<LottoUser> targetUsers = lottoUserResolver.resolve(requestedUserIds);

        log.info("[Task] Batch start mode={} trigger={} userCount={}", mode, trigger, targetUsers.size());

        Instant startedAt = Instant.now();
        long startedNano = System.nanoTime();

        int successUsers = 0;
        int failedUsers = 0;
        for (LottoUser user : targetUsers) {
            if (runUser(user, mode, executionFlow)) {
                successUsers++;
            } else {
                failedUsers++;
            }
        }

        Instant endedAt = Instant.now();
        long durationMs = (System.nanoTime() - startedNano) / 1_000_000;
        int totalUsers = targetUsers.size();
        ExecutionStatus status = ExecutionStatus.fromCounts(totalUsers, failedUsers);

        LottoExecution result = new LottoExecution(
                mode,
                trigger,
                status,
                startedAt,
                endedAt,
                durationMs,
                totalUsers,
                successUsers,
                failedUsers
        );

        log.info("[Task] Batch complete mode={} trigger={} status={} success={} failed={} durationMs={}",
                mode, trigger, status, successUsers, failedUsers, durationMs);

        return result;
    }

    private boolean runUser(LottoUser user, TaskMode mode, LottoUserExecutionFlow executionFlow) {
        try (LottoAutomationSession session = lottoAutomationPort.openSession()) {
            log.info("[Task][{}] Start mode={}", user.id(), mode);

            LottoUserExecutionContext executionContext = executionFlow.execute(
                    new LottoUserAutomationExecutor(lottoAutomationPort, session, user)
            );

            lottoNotificationFactory.createSuccessMessage(
                            user,
                            executionContext.accountSnapshot(),
                            executionContext.results()
                    )
                    .ifPresent(message -> sendNotification(user, message, "success"));

            log.info("[Task][{}] Success mode={} deposit={}",
                    user.id(), mode, executionContext.accountSnapshot().deposit());
            return true;
        } catch (LottoAutomationException exception) {
            log.error("[Task][{}] Failed mode={} step={}", user.id(), mode, exception.getStepType(), exception);
            sendNotification(user, lottoNotificationFactory.createFailureMessage(user, mode, exception), "failure");
            return false;
        } catch (Exception exception) {
            log.error("[Task][{}] Failed mode={} step=unknown", user.id(), mode, exception);
            sendNotification(user, lottoNotificationFactory.createFailureMessage(user, mode, exception), "failure");
            return false;
        }
    }

    private void sendNotification(LottoUser user, NotificationMessage message, String kind) {
        try {
            sendNotificationPort.send(message);
            log.info("[Notify][{}] {} notification sent", user.id(), kind);
        } catch (Exception notificationError) {
            log.error("[Notify][{}] {} notification failed", user.id(), kind, notificationError);
        }
    }
}
