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
            // 세션을 열기 전이라 브라우저 permit을 잡지 않은 상태 → 바로 통지해도 무방.
            return aborted(user, mode, "작업 시작 전");
        }

        // 브라우저 작업은 try-with-resources 안에서만 수행하고, 통지(SMTP)는 세션이 닫혀 permit이 해제된 뒤에 보낸다.
        // (성공/실패/중단 통지가 브라우저 슬롯을 붙잡아 다른 사용자가 세마포어에서 대기하는 것을 방지)
        LottoAccountSnapshot accountSnapshot = null;
        List<CheckResult> results = null;
        String abortPhase = null;
        boolean checkAbortedAfterCompletion = false;
        try (LottoAutomationSession session = lottoAutomationPort.openSession()) {
            try {
                if (abortSignal.get()) {
                    session.markFailed();
                    abortPhase = "세션 확보 직후";
                } else {
                    log.info("[Task][{}] Start mode={}", user.id(), mode);
                    accountSnapshot = runStep(StepType.LOGIN, user, () -> lottoAutomationPort.login(session, user));

                    if (mode == TaskMode.PURCHASE) {
                        Optional<PurchaseReceipt> purchaseReceipt = runStep(StepType.PURCHASE, user,
                                () -> lottoAutomationPort.buy(session, user, abortSignal::get));
                        if (purchaseReceipt.isEmpty()) {
                            session.markFailed();
                            abortPhase = "최종 확정 미제출/중단";
                        } else {
                            results = List.of(runStep(StepType.CHECK, user,
                                    () -> lottoAutomationPort.check(session, purchaseReceipt.get())));
                        }
                    } else {
                        results = runStep(StepType.CHECK, user, () -> lottoAutomationPort.check(session));
                    }
                }

                if (abortPhase == null && mode == TaskMode.CHECK && abortSignal.get()) {
                    session.markFailed();
                    checkAbortedAfterCompletion = true;
                }
            } catch (RuntimeException | Error exception) {
                session.markFailed();
                throw exception;
            }
        } catch (LottoAutomationException exception) {
            // try-with-resources가 세션을 이미 닫음(permit 해제) → 아래 통지가 브라우저 슬롯을 점유하지 않는다.
            log.error("[Task][{}] Failed mode={} step={}", user.id(), mode, exception.getStepType(), exception);
            sendNotification(user, lottoNotificationFactory.createFailureMessage(user, mode, exception), "failure");
            return false;
        } catch (Exception exception) {
            log.error("[Task][{}] Failed mode={} step=unknown", user.id(), mode, exception);
            sendNotification(user, lottoNotificationFactory.createFailureMessage(user, mode, exception), "failure");
            return false;
        }

        // 여기서부터 세션이 닫혀(permit 해제된) 상태 → 통지는 브라우저 슬롯을 붙잡지 않는다.
        if (abortPhase != null) {
            return aborted(user, mode, abortPhase);
        }
        if (checkAbortedAfterCompletion || mode == TaskMode.CHECK && abortSignal.get()) {
            // CHECK 진행 중 호출자 타임아웃 → 호출자는 이미 실패 보고. 뒤늦은 성공 통지로 상태 불일치를 만들지 않는다.
            log.warn("[Task][{}] Check completed but aborted meanwhile - suppressing success notification", user.id());
            return false;
        }

        try {
            lottoNotificationFactory.createCheckSuccessMessage(user, accountSnapshot, results)
                    .ifPresent(message -> sendNotification(user, message, "success"));
            log.info("[Task][{}] Success mode={} deposit={}", user.id(), mode, accountSnapshot.deposit());
            return true;
        } catch (Exception exception) {
            // 성공 메시지 포맷팅(스크랩 데이터 파싱 등) 중 오류가 나도 사용자에게 실패 통지를 보낸다.
            // (브라우저 작업은 끝났고 세션이 닫혀 permit이 해제된 상태라 통지가 브라우저 슬롯을 점유하지 않는다)
            log.error("[Task][{}] Failed mode={} step=notify", user.id(), mode, exception);
            sendNotification(user, lottoNotificationFactory.createFailureMessage(user, mode, exception), "failure");
            return false;
        }
    }

    /**
     * 실제 작업 수행 전에 중단된 경우의 처리. PURCHASE는 스케줄 구매가 조용히 누락되지 않도록 실패 통지를 보낸다.
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
