package org.nowstart.lotto.application.service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort;
import org.nowstart.lotto.domain.exception.InvalidManualUserSelectionException;
import org.nowstart.lotto.domain.model.LottoUser;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LottoUserResolver {

    private final LoadLottoUsersPort loadLottoUsersPort;

    public List<LottoUser> resolve(List<String> requestedUserIds) {
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
}
