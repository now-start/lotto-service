package org.nowstart.lotto.service;

import com.microsoft.playwright.Page;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.data.dto.BatchExecutionResult;
import org.nowstart.lotto.data.dto.LottoResultDto;
import org.nowstart.lotto.data.dto.LottoUserDto;
import org.nowstart.lotto.data.dto.PageDto;
import org.nowstart.lotto.data.exception.InvalidManualUserSelectionException;
import org.nowstart.lotto.data.exception.StepExecutionException;
import org.nowstart.lotto.data.properties.LottoProperties;
import org.nowstart.lotto.data.type.ExecutionStatus;
import org.nowstart.lotto.data.type.TaskMode;
import org.nowstart.lotto.data.type.TriggerType;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LottoService {

    private final LottoProperties lottoProperties;
    private final PageService pageService;
    private final LottoLoginService lottoLoginService;
    private final LottoPurchaseService lottoPurchaseService;
    private final LottoResultService lottoResultService;
    private final LottoNotificationService lottoNotificationService;

    public BatchExecutionResult execute(TaskMode mode, TriggerType trigger, List<String> userIds) {
        List<LottoProperties.User> targetUsers = resolveTargetUsers(userIds);
        return runBatch(mode, trigger, targetUsers);
    }

    private List<LottoProperties.User> resolveTargetUsers(List<String> requestedUserIds) {
        List<LottoProperties.User> allUsers = lottoProperties.getUsers();
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

        Map<String, LottoProperties.User> usersById = allUsers.stream()
                .collect(java.util.stream.Collectors.toMap(
                        LottoProperties.User::getId,
                        user -> user,
                        (existing, ignored) -> existing,
                        java.util.LinkedHashMap::new
                ));

        List<String> invalidUserIds = normalizedUserIds.stream()
                .filter(id -> !usersById.containsKey(id))
                .toList();

        if (!invalidUserIds.isEmpty()) {
            throw new InvalidManualUserSelectionException(
                    invalidUserIds,
                    new java.util.ArrayList<>(new LinkedHashSet<>(usersById.keySet()))
            );
        }

        return normalizedUserIds.stream()
                .map(usersById::get)
                .toList();
    }

    private BatchExecutionResult runBatch(
            TaskMode mode,
            TriggerType trigger,
            List<LottoProperties.User> targetUsers
    ) {
        log.info("[Task] Batch start mode={} trigger={} userCount={}", mode, trigger, targetUsers.size());
        Instant startedAt = Instant.now();
        long startedNano = System.nanoTime();

        int successUsers = 0;
        int failedUsers = 0;
        for (LottoProperties.User user : targetUsers) {
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

        BatchExecutionResult result = new BatchExecutionResult(
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

    private boolean runUser(LottoProperties.User user, TaskMode mode) {
        try (PageDto pageDto = pageService.createManagedPage()) {
            Page page = pageDto.page();
            log.info("[Task][{}] Start mode={}", user.getId(), mode);

            LottoUserDto lottoUserDto = runStep("login", user, () -> lottoLoginService.login(page, user));
            if (mode.isBuyEnabled()) {
                runStep("purchase", user, () -> {
                    lottoPurchaseService.buy(page, user);
                    return null;
                });
            }

            List<LottoResultDto> results = runStep("check", user, () -> lottoResultService.check(page));
            lottoNotificationService.sendSuccess(user, mode, lottoUserDto, results);

            log.info("[Task][{}] Success mode={} deposit={}", user.getId(), mode, lottoUserDto.getDeposit());
            return true;
        } catch (StepExecutionException stepExecutionException) {
            log.error("[Task][{}] Failed mode={} step={}", user.getId(), mode, stepExecutionException.getStep(), stepExecutionException);
            lottoNotificationService.sendFailure(user, mode, stepExecutionException);
            return false;
        } catch (Exception exception) {
            log.error("[Task][{}] Failed mode={} step=unknown", user.getId(), mode, exception);
            lottoNotificationService.sendFailure(user, mode, exception);
            return false;
        }
    }

    private <T> T runStep(
            String step,
            LottoProperties.User user,
            java.util.function.Supplier<T> action
    ) {
        try {
            return action.get();
        } catch (Exception exception) {
            throw new StepExecutionException(step, user.getId(), exception);
        }
    }
}
