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
            return aborted(user, mode, "작업 시작 전");
        }
        try (LottoAutomationSession session = lottoAutomationPort.openSession()) {
            // 세션 확보(세마포어 대기 포함) 이후, 어떤 사이트 조작보다 먼저 취소 여부를 재확인한다.
            if (abortSignal.get()) {
                return aborted(user, mode, "세션 확보 직후");
            }
            log.info("[Task][{}] Start mode={}", user.id(), mode);

            LottoAccountSnapshot accountSnapshot = runStep(StepType.LOGIN, user,
                    () -> lottoAutomationPort.login(session, user));

            List<CheckResult> results;
            if (mode == TaskMode.PURCHASE) {
                Optional<PurchaseReceipt> purchaseReceipt = runStep(
                        StepType.PURCHASE,
                        user,
                        () -> lottoAutomationPort.buy(session, user, abortSignal::get)
                );
                if (purchaseReceipt.isEmpty()) {
                    return aborted(user, mode, "최종 확정 미제출/중단");
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

            // CHECK가 진행되는 동안 호출자 타임아웃(abort)이 발생했다면, 호출자는 이미 실패로 보고했다.
            // 취소 불가한 조회 결과에 대해 뒤늦게 성공 통지를 보내 상태 불일치를 만들지 않도록 억제한다.
            // (PURCHASE는 실제 구매가 성사됐을 수 있으므로 성공 통지를 유지해 사용자가 반드시 알 수 있게 한다.)
            if (mode == TaskMode.CHECK && abortSignal.get()) {
                log.warn("[Task][{}] Check completed but aborted meanwhile - suppressing success notification", user.id());
                return false;
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

    /**
     * 호출자 타임아웃 등으로 실제 작업을 수행하기 전에 중단된 경우의 처리.
     * PURCHASE는 스케줄 구매가 조용히 누락되지 않도록 예외 경로와 동일한 실패 통지를 보낸다.
     * CHECK는 조회 미수행이 치명적이지 않으므로 통지하지 않는다(호출자가 이미 타임아웃 실패로 보고).
     */
    private boolean aborted(LottoUser user, TaskMode mode, String phase) {
        log.warn("[Task][{}] Aborted ({}) - 호출자 타임아웃 mode={}", user.id(), phase, mode);
        if (mode == TaskMode.PURCHASE) {
            LottoAutomationException abortException = new LottoAutomationException(
                    StepType.PURCHASE,
                    user.id(),
                    new IllegalStateException("구매가 최종 확정 전에 중단/미제출되었습니다 (" + phase + ")"));
            sendNotification(user, lottoNotificationFactory.createFailureMessage(user, mode, abortException), "failure");
        }
        return false;
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
