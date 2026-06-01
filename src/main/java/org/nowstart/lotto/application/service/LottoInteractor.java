package org.nowstart.lotto.application.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.application.port.in.LottoUseCase;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort;
import org.nowstart.lotto.application.port.out.LottoAutomationPort;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.CheckResult;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.PurchaseReceipt;
import org.nowstart.lotto.application.port.out.LottoAutomationSession;
import org.nowstart.lotto.application.port.out.SendNotificationPort;
import org.nowstart.lotto.domain.exception.InvalidManualUserSelectionException;
import org.nowstart.lotto.domain.exception.LottoAutomationException;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.LottoAccountSnapshot;
import org.nowstart.lotto.application.port.in.LottoUseCase.LottoExecution;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort.LottoUser;
import org.nowstart.lotto.application.port.out.SendNotificationPort.NotificationMessage;
import org.nowstart.lotto.domain.type.ExecutionStatus;
import org.nowstart.lotto.domain.type.StepType;
import org.nowstart.lotto.domain.type.TaskMode;
import org.nowstart.lotto.domain.type.TriggerType;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LottoInteractor implements LottoUseCase {

    private final LoadLottoUsersPort loadLottoUsersPort;
    private final LottoAutomationPort lottoAutomationPort;
    private final SendNotificationPort sendNotificationPort;
    private final LottoNotificationFactory lottoNotificationFactory;

    @Override
    public LottoExecution check(TargetCommand command) {
        return execute(command.trigger(), command.userIds(), TaskMode.CHECK);
    }

    @Override
    public LottoExecution purchase(TargetCommand command) {
        return execute(command.trigger(), command.userIds(), TaskMode.PURCHASE);
    }

    private LottoExecution execute(TriggerType trigger, List<String> requestedUserIds, TaskMode mode) {
        List<LottoUser> targetUsers = resolveTargetUsers(requestedUserIds);

        log.info("[Task] Batch start mode={} trigger={} userCount={}", mode, trigger, targetUsers.size());

        Instant startedAt = Instant.now();
        long startedNano = System.nanoTime();

        int successUsers = 0;
        int failedUsers = 0;
        for (LottoUser user : targetUsers) {
            if (runUser(user, mode)) {
                successUsers++;
            } else {
                failedUsers++;
            }
        }

        Instant endedAt = Instant.now();
        long durationMs = (System.nanoTime() - startedNano) / 1_000_000;
        int totalUsers = targetUsers.size();
        ExecutionStatus status = ExecutionStatus.fromCounts(totalUsers, failedUsers);

        LottoExecution execution = new LottoExecution(
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

        return execution;
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

    private List<LottoUser> resolveTargetUsers(List<String> requestedUserIds) {
        List<LottoUser> allUsers = loadLottoUsersPort.loadUsers();
        if (requestedUserIds.isEmpty()) {
            return allUsers;
        }

        Map<String, LottoUser> usersById = new LinkedHashMap<>();
        for (LottoUser user : allUsers) {
            usersById.putIfAbsent(user.id(), user);
        }

        List<String> invalidUserIds = requestedUserIds.stream()
                .filter(id -> !usersById.containsKey(id))
                .toList();

        if (!invalidUserIds.isEmpty()) {
            throw new InvalidManualUserSelectionException(
                    invalidUserIds,
                    List.copyOf(new LinkedHashSet<>(usersById.keySet()))
            );
        }

        return requestedUserIds.stream()
                .map(usersById::get)
                .toList();
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
