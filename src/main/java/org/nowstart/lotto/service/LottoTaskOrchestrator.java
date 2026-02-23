package org.nowstart.lotto.service;

import com.microsoft.playwright.Page;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.data.dto.BatchExecutionResult;
import org.nowstart.lotto.data.dto.LottoResultDto;
import org.nowstart.lotto.data.dto.LottoUserDto;
import org.nowstart.lotto.data.dto.PageDto;
import org.nowstart.lotto.data.exception.StepExecutionException;
import org.nowstart.lotto.data.properties.LottoProperties;
import org.nowstart.lotto.data.type.ExecutionStatus;
import org.nowstart.lotto.data.type.TaskMode;
import org.nowstart.lotto.data.type.TriggerType;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LottoTaskOrchestrator {

    private final PageService pageService;
    private final LottoProperties lottoProperties;
    private final LottoLoginService lottoLoginService;
    private final LottoPurchaseService lottoPurchaseService;
    private final LottoResultService lottoResultService;
    private final LottoNotificationService lottoNotificationService;
    private final RetryExecutor retryExecutor;

    public BatchExecutionResult execute(TaskMode mode, TriggerType trigger) {
        log.info("[Task] Batch start mode={} trigger={} userCount={}", mode, trigger, lottoProperties.getUsers().size());
        Instant startedAt = Instant.now();
        long startedNano = System.nanoTime();

        int successUsers = 0;
        int failedUsers = 0;
        for (LottoProperties.User user : lottoProperties.getUsers()) {
            if (executeForUser(user, mode)) {
                successUsers++;
            } else {
                failedUsers++;
            }
        }

        Instant endedAt = Instant.now();
        long durationMs = (System.nanoTime() - startedNano) / 1_000_000;
        int totalUsers = lottoProperties.getUsers().size();
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

    private boolean executeForUser(LottoProperties.User user, TaskMode mode) {
        try (PageDto pageDto = pageService.createManagedPage()) {
            Page page = pageDto.page();
            log.info("[Task][{}] Start mode={}", user.getId(), mode);

            LottoUserDto lottoUserDto = executeStepWithRetry(mode, "login", user, () -> lottoLoginService.login(page, user));
            if (mode.isBuyEnabled()) {
                executeStepWithRetry(mode, "purchase", user, () -> {
                    lottoPurchaseService.buy(page, user);
                    return null;
                });
            }

            List<LottoResultDto> results = executeStepWithRetry(mode, "check", user, () -> lottoResultService.check(page));
            lottoNotificationService.sendSuccess(user, lottoUserDto, results);

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

    private <T> T executeStepWithRetry(
            TaskMode mode,
            String step,
            LottoProperties.User user,
            java.util.function.Supplier<T> action
    ) {
        try {
            return retryExecutor.execute(step, action);
        } catch (Exception exception) {
            throw new StepExecutionException(step, user.getId(), exception);
        }
    }
}
