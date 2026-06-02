package org.nowstart.lotto.application.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.application.port.in.LottoUseCase;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort;
import org.nowstart.lotto.domain.exception.InvalidManualUserSelectionException;
import org.nowstart.lotto.application.port.in.LottoUseCase.LottoExecution;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort.LottoUser;
import org.nowstart.lotto.domain.type.ExecutionStatus;
import org.nowstart.lotto.domain.type.TaskMode;
import org.nowstart.lotto.domain.type.TriggerType;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LottoInteractor implements LottoUseCase {

    private final LoadLottoUsersPort loadLottoUsersPort;
    private final LottoUserRunner lottoUserRunner;

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

        UserExecutionCounts userExecutionCounts = runUsers(targetUsers, mode);
        int successUsers = userExecutionCounts.successUsers();
        int failedUsers = userExecutionCounts.failedUsers();

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

    private UserExecutionCounts runUsers(List<LottoUser> targetUsers, TaskMode mode) {
        List<UserTask> userTasks = new ArrayList<>();
        try {
            for (LottoUser user : targetUsers) {
                userTasks.add(new UserTask(user, lottoUserRunner.runAsync(user, mode)));
            }

            int successUsers = 0;
            int failedUsers = 0;
            for (UserTask userTask : userTasks) {
                if (joinUserTask(userTask, mode)) {
                    successUsers++;
                } else {
                    failedUsers++;
                }
            }
            return new UserExecutionCounts(successUsers, failedUsers);
        } catch (Error error) {
            cancelRemaining(userTasks);
            throw error;
        }
    }

    private boolean joinUserTask(UserTask userTask, TaskMode mode) {
        try {
            return userTask.future().join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Error error) {
                throw error;
            }
            log.error("[Task][{}] Failed mode={} step=async", userTask.user().id(), mode,
                    cause == null ? exception : cause);
            return false;
        }
    }

    private void cancelRemaining(List<UserTask> userTasks) {
        userTasks.stream()
                .map(UserTask::future)
                .filter(future -> !future.isDone())
                .forEach(future -> future.cancel(true));
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

    private record UserTask(LottoUser user, CompletableFuture<Boolean> future) {
    }

    private record UserExecutionCounts(int successUsers, int failedUsers) {
    }
}
