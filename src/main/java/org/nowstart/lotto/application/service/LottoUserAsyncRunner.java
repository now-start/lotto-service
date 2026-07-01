package org.nowstart.lotto.application.service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
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
    public CompletableFuture<Boolean> runAsync(LottoUser user, TaskMode mode, AtomicBoolean abortSignal) {
        return CompletableFuture.completedFuture(runUser(user, mode, abortSignal));
    }

    private boolean runUser(LottoUser user, TaskMode mode, AtomicBoolean abortSignal) {
        if (abortSignal.get()) {
            log.warn("[Task][{}] Aborted before start (호출자 타임아웃) mode={}", user.id(), mode);
            return false;
        }
        try (LottoAutomationSession session = lottoAutomationPort.openSession()) {
            // 세션 확보(세마포어 대기 포함) 이후, 어떤 사이트 조작보다 먼저 취소 여부를 재확인한다.
            if (abortSignal.get()) {
                log.warn("[Task][{}] Aborted after acquiring session (호출자 타임아웃) mode={}", user.id(), mode);
                return false;
            }
            log.info("[Task][{}] Start mode={}", user.id(), mode);

            LottoAccountSnapshot accountSnapshot = runStep(StepType.LOGIN, user,
                    () -> lottoAutomationPort.login(session, user));

            List<CheckResult> results;
            if (mode == TaskMode.PURCHASE) {
                // 실결제 직전 abort는 구매 executor(buy) 내부에서 최종 확인한다(Optional.empty = 미수행).
                Optional<PurchaseReceipt> purchaseReceipt = runStep(
                        StepType.PURCHASE,
                        user,
                        () -> lottoAutomationPort.buy(session, user, abortSignal::get)
                );
                if (purchaseReceipt.isEmpty()) {
                    log.warn("[Task][{}] Aborted before final purchase confirmation (호출자 타임아웃) mode={}",
                            user.id(), mode);
                    // 예외 경로와 동일하게 실패 통지를 보낸다 — 스케줄 구매가 조용히 누락되지 않도록.
                    LottoAutomationException abortException = new LottoAutomationException(
                            StepType.PURCHASE,
                            user.id(),
                            new IllegalStateException("호출자 타임아웃으로 최종 확정 전 구매가 중단되었습니다"));
                    sendNotification(user,
                            lottoNotificationFactory.createFailureMessage(user, mode, abortException), "failure");
                    return false;
                }
                CheckResult latestPurchaseResult = runStep(
                        StepType.CHECK,
                        user,
                        () -> lottoAutomationPort.check(session, purchaseReceipt.get())
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
