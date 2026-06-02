package org.nowstart.lotto.application.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort.LottoUser;
import org.nowstart.lotto.application.port.out.LottoAutomationPort;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.CheckResult;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.LottoAccountSnapshot;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.PurchaseReceipt;
import org.nowstart.lotto.application.port.out.LottoAutomationSession;
import org.nowstart.lotto.application.port.out.SendNotificationPort;
import org.nowstart.lotto.application.port.out.SendNotificationPort.NotificationMessage;
import org.nowstart.lotto.domain.exception.LottoAutomationException;
import org.nowstart.lotto.domain.type.StepType;
import org.nowstart.lotto.domain.type.TaskMode;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LottoUserAsyncRunner implements LottoUserRunner {

    private final LottoAutomationPort lottoAutomationPort;
    private final SendNotificationPort sendNotificationPort;
    private final LottoNotificationFactory lottoNotificationFactory;

    @Async("lottoTaskExecutor")
    @Override
    public CompletableFuture<Boolean> runAsync(LottoUser user, TaskMode mode) {
        return CompletableFuture.completedFuture(runUser(user, mode));
    }

    private boolean runUser(LottoUser user, TaskMode mode) {
        try (LottoAutomationSession session = lottoAutomationPort.openSession()) {
            log.info("[Task][{}] Start mode={}", user.id(), mode);

            LottoAccountSnapshot accountSnapshot = runStep(StepType.LOGIN, user,
                    () -> lottoAutomationPort.login(session, user));

            List<CheckResult> results;
            if (mode == TaskMode.PURCHASE) {
                PurchaseReceipt purchaseReceipt = runStep(
                        StepType.PURCHASE,
                        user,
                        () -> lottoAutomationPort.buy(session, user)
                );
                CheckResult latestPurchaseResult = runStep(
                        StepType.CHECK,
                        user,
                        () -> lottoAutomationPort.check(session, purchaseReceipt)
                );
                results = List.of(latestPurchaseResult);
            } else {
                results = runStep(StepType.CHECK, user, () -> lottoAutomationPort.check(session));
            }

            lottoNotificationFactory.createCheckSuccessMessage(user, accountSnapshot, results)
                    .ifPresent(message -> sendNotification(user, message, "success"));

            log.info("[Task][{}] Success mode={} deposit={}", user.id(), mode, accountSnapshot.deposit());
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

    private <T> T runStep(StepType stepType, LottoUser user, StepAction<T> action) {
        try {
            return action.run();
        } catch (LottoAutomationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new LottoAutomationException(stepType, user.id(), exception);
        }
    }

    @FunctionalInterface
    private interface StepAction<T> {

        T run();
    }
}
