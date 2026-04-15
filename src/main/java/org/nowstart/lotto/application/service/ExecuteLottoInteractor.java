package org.nowstart.lotto.application.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.application.dto.ExecuteLottoCommand;
import org.nowstart.lotto.application.port.in.ExecuteLottoUseCase;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort;
import org.nowstart.lotto.application.port.out.LottoAutomationPort;
import org.nowstart.lotto.application.port.out.LottoAutomationSession;
import org.nowstart.lotto.application.port.out.SendNotificationPort;
import org.nowstart.lotto.domain.exception.InvalidManualUserSelectionException;
import org.nowstart.lotto.domain.exception.LottoAutomationException;
import org.nowstart.lotto.domain.model.LottoAccountSnapshot;
import org.nowstart.lotto.domain.model.LottoExecution;
import org.nowstart.lotto.domain.model.LottoResult;
import org.nowstart.lotto.domain.model.LottoUser;
import org.nowstart.lotto.domain.model.NotificationMessage;
import org.nowstart.lotto.domain.type.ExecutionStatus;
import org.nowstart.lotto.domain.type.StepType;
import org.nowstart.lotto.domain.type.TaskMode;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecuteLottoInteractor implements ExecuteLottoUseCase {

    private final LoadLottoUsersPort loadLottoUsersPort;
    private final LottoAutomationPort lottoAutomationPort;
    private final SendNotificationPort sendNotificationPort;
    private final LottoNotificationFactory lottoNotificationFactory;

    @Override
    public LottoExecution execute(ExecuteLottoCommand command) {
        List<LottoUser> targetUsers = resolveTargetUsers(command.userIds());

        log.info("[Task] Batch start mode={} trigger={} userCount={}",
                command.mode(), command.trigger(), targetUsers.size());

        Instant startedAt = Instant.now();
        long startedNano = System.nanoTime();

        int successUsers = 0;
        int failedUsers = 0;
        for (LottoUser user : targetUsers) {
            if (runUser(user, command.mode())) {
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
                command.mode(),
                command.trigger(),
                status,
                startedAt,
                endedAt,
                durationMs,
                totalUsers,
                successUsers,
                failedUsers
        );

        log.info("[Task] Batch complete mode={} trigger={} status={} success={} failed={} durationMs={}",
                command.mode(), command.trigger(), status, successUsers, failedUsers, durationMs);

        return result;
    }

    private List<LottoUser> resolveTargetUsers(List<String> requestedUserIds) {
        List<LottoUser> allUsers = loadLottoUsersPort.loadUsers();
        if (requestedUserIds == null || requestedUserIds.isEmpty()) {
            return allUsers;
        }

        List<String> normalizedUserIds = requestedUserIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .distinct()
                .toList();

        if (normalizedUserIds.isEmpty()) {
            return allUsers;
        }

        Map<String, LottoUser> usersById = new LinkedHashMap<>();
        for (LottoUser user : allUsers) {
            usersById.putIfAbsent(user.id(), user);
        }

        List<String> invalidUserIds = normalizedUserIds.stream()
                .filter(id -> !usersById.containsKey(id))
                .toList();

        if (!invalidUserIds.isEmpty()) {
            throw new InvalidManualUserSelectionException(
                    invalidUserIds,
                    List.copyOf(new LinkedHashSet<>(usersById.keySet()))
            );
        }

        return normalizedUserIds.stream()
                .map(usersById::get)
                .toList();
    }

    private boolean runUser(LottoUser user, TaskMode mode) {
        try (LottoAutomationSession session = lottoAutomationPort.openSession()) {
            log.info("[Task][{}] Start mode={}", user.id(), mode);

            LottoAccountSnapshot accountSnapshot = runStep(StepType.LOGIN, user,
                    () -> lottoAutomationPort.login(session, user));

            if (mode.isBuyEnabled()) {
                runStep(StepType.PURCHASE, user, () -> {
                    lottoAutomationPort.buy(session, user);
                    return null;
                });
            }

            List<LottoResult> results = runStep(StepType.CHECK, user,
                    () -> lottoAutomationPort.check(session));

            lottoNotificationFactory.createSuccessMessage(user, accountSnapshot, results)
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

    private <T> T runStep(StepType stepType, LottoUser user, Supplier<T> action) {
        try {
            return action.get();
        } catch (LottoAutomationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new LottoAutomationException(stepType, user.id(), exception);
        }
    }
}
