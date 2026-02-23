package org.nowstart.lotto.service;

import com.microsoft.playwright.Page;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.nowstart.lotto.data.dto.BatchExecutionResult;
import org.nowstart.lotto.data.dto.LottoUserDto;
import org.nowstart.lotto.data.exception.InvalidManualUserSelectionException;
import org.nowstart.lotto.data.properties.LottoProperties;
import org.nowstart.lotto.data.type.TaskMode;
import org.nowstart.lotto.data.type.TriggerType;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LottoService {

    private final LottoProperties lottoProperties;
    private final LottoLoginService lottoLoginService;
    private final LottoTaskOrchestrator lottoTaskOrchestrator;

    public LottoUserDto loginLotto(Page page, LottoProperties.User user) {
        return lottoLoginService.login(page, user);
    }

    public BatchExecutionResult execute(TaskMode mode, TriggerType trigger) {
        return lottoTaskOrchestrator.execute(mode, trigger);
    }

    public BatchExecutionResult execute(TaskMode mode, TriggerType trigger, List<String> userIds) {
        List<LottoProperties.User> targetUsers = resolveTargetUsers(userIds);
        return lottoTaskOrchestrator.execute(mode, trigger, targetUsers);
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
}
