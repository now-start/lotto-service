package org.nowstart.lotto.application.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.application.port.in.LottoUseCase;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort;
import org.nowstart.lotto.config.LottoProperties;
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
    private final LottoProperties lottoProperties;

    // 인스턴스 내 중복 실행 방지(수동 API + 스케줄 동시 실행 등). 다중 인스턴스 환경은 분산 락/리더 선출이 별도로 필요하다.
    // 키는 실제 비동기 작업이 종료될 때(whenComplete) 해제한다 — 호출자 타임아웃 시점에 해제하면
    // 작업이 백그라운드에서 계속 도는 동안 같은 사용자의 중복 실행이 시작될 수 있으므로 금물.
    private final Set<String> inFlightKeys = ConcurrentHashMap.newKeySet();

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

        UserExecutionCounts counts = runUsers(targetUsers, mode);
        int successUsers = counts.successUsers();
        int skippedUsers = counts.skippedUsers();
        // 이 호출에서 실행되지 못한 skip 사용자(다른 실행이 선점)도 '비성공'으로 집계한다.
        // 그러지 않으면 전량 skip 시 totalUsers=0 → ExecutionStatus.fromCounts(0,0)=SUCCESS 로 오인된다.
        int failedUsers = counts.failedUsers() + skippedUsers;

        Instant endedAt = Instant.now();
        long durationMs = (System.nanoTime() - startedNano) / 1_000_000;
        int totalUsers = successUsers + failedUsers;
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

        log.info("[Task] Batch complete mode={} trigger={} status={} success={} failed={} skipped={} durationMs={}",
                mode, trigger, status, successUsers, counts.failedUsers(), skippedUsers, durationMs);

        return execution;
    }

    private UserExecutionCounts runUsers(List<LottoUser> targetUsers, TaskMode mode) {
        List<UserTask> userTasks = new ArrayList<>();
        int skippedUsers = 0;
        for (LottoUser user : targetUsers) {
            String key = mode + ":" + user.id();
            if (!inFlightKeys.add(key)) {
                skippedUsers++;
                log.warn("[Task][{}] Skip - 동일 사용자 작업이 이미 실행 중 mode={}", user.id(), mode);
                continue;
            }
            // per-user 타임아웃 기준점을 '제출 시점'으로 고정한다 — join 시점 기준이면 앞선 hung 사용자를
            // 기다린 시간만큼 뒤 사용자의 제한이 늘어나(N배) 설정값을 크게 초과할 수 있다.
            long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(lottoProperties.getUserTaskTimeoutMs());
            AtomicBoolean abortSignal = new AtomicBoolean(false);
            CompletableFuture<Boolean> future;
            try {
                future = lottoUserRunner.runAsync(user, mode, abortSignal);
            } catch (RuntimeException | Error submitError) {
                // 제출 실패(프록시/러너 치명 오류 등) 시 키가 남아 이후 실행이 영구 skip되지 않도록 해제 후 전파.
                inFlightKeys.remove(key);
                throw submitError;
            }
            // 실제 작업 종료(성공/실패/취소) 시점에만 in-flight 키를 해제한다.
            future.whenComplete((result, error) -> inFlightKeys.remove(key));
            userTasks.add(new UserTask(user, key, deadlineNanos, abortSignal, future));
        }

        try {
            int successUsers = 0;
            int failedUsers = 0;
            for (UserTask userTask : userTasks) {
                if (joinUserTask(userTask, mode)) {
                    successUsers++;
                } else {
                    failedUsers++;
                }
            }
            return new UserExecutionCounts(successUsers, failedUsers, skippedUsers);
        } catch (Error error) {
            cancelRemaining(userTasks);
            throw error;
        }
    }

    private boolean joinUserTask(UserTask userTask, TaskMode mode) {
        long remainingMs = Math.max(0L, (userTask.deadlineNanos() - System.nanoTime()) / 1_000_000L);
        try {
            return userTask.future().get(remainingMs, TimeUnit.MILLISECONDS);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Error error) {
                throw error;
            }
            log.error("[Task][{}] Failed mode={} step=async", userTask.user().id(), mode,
                    cause == null ? exception : cause);
            return false;
        } catch (TimeoutException exception) {
            // 아직 실행/큐잉 중인 작업에 중단 신호를 보낸다 — 세마포어 대기 중이던 작업이 뒤늦게
            // 실거래(구매)를 수행하는 것을 방지(러너가 구매 직전 abortSignal을 확인한다).
            // 실제 작업은 백그라운드에서 안전하게 종료되며, in-flight 키는 whenComplete가 해제한다.
            userTask.abortSignal().set(true);
            log.error("[Task][{}] Timed out mode={} (제한 {}ms 초과 - 중단 신호 전송, 작업은 안전 지점에서 종료됨)",
                    userTask.user().id(), mode, lottoProperties.getUserTaskTimeoutMs());
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            userTask.abortSignal().set(true);
            log.error("[Task][{}] Interrupted mode={}", userTask.user().id(), mode, exception);
            return false;
        }
    }

    private void cancelRemaining(List<UserTask> userTasks) {
        userTasks.forEach(userTask -> {
            userTask.abortSignal().set(true);
            if (!userTask.future().isDone()) {
                userTask.future().cancel(true);
            }
        });
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

    private record UserTask(
            LottoUser user,
            String key,
            long deadlineNanos,
            AtomicBoolean abortSignal,
            CompletableFuture<Boolean> future
    ) {
    }

    private record UserExecutionCounts(int successUsers, int failedUsers, int skippedUsers) {
    }
}
