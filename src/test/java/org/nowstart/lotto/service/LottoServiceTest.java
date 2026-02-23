package org.nowstart.lotto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nowstart.lotto.data.dto.BatchExecutionResult;
import org.nowstart.lotto.data.exception.InvalidManualUserSelectionException;
import org.nowstart.lotto.data.properties.LottoProperties;
import org.nowstart.lotto.data.type.ExecutionStatus;
import org.nowstart.lotto.data.type.TaskMode;
import org.nowstart.lotto.data.type.TriggerType;

@ExtendWith(MockitoExtension.class)
class LottoServiceTest {

    @Mock
    private LottoLoginService lottoLoginService;

    @Mock
    private LottoTaskOrchestrator lottoTaskOrchestrator;

    private LottoProperties lottoProperties;
    private LottoService lottoService;

    @BeforeEach
    void setUp() {
        lottoProperties = new LottoProperties();
        lottoProperties.setUsers(new ArrayList<>());
        lottoProperties.getUsers().add(createUser("user1"));
        lottoProperties.getUsers().add(createUser("user2"));
        lottoProperties.getUsers().add(createUser("user3"));

        lottoService = new LottoService(lottoProperties, lottoLoginService, lottoTaskOrchestrator);
    }

    @Test
    void shouldExecuteAllUsersWhenUserIdsMissing() {
        BatchExecutionResult expected = createResult(3, 3, 0);
        when(lottoTaskOrchestrator.execute(eq(TaskMode.CHECK_ONLY), eq(TriggerType.MANUAL), anyList()))
                .thenReturn(expected);

        BatchExecutionResult result = lottoService.execute(TaskMode.CHECK_ONLY, TriggerType.MANUAL, null);

        assertThat(result).isEqualTo(expected);
        verify(lottoTaskOrchestrator).execute(eq(TaskMode.CHECK_ONLY), eq(TriggerType.MANUAL), eq(lottoProperties.getUsers()));
    }

    @Test
    void shouldExecuteSelectedUsersOnly() {
        BatchExecutionResult expected = createResult(2, 2, 0);
        List<String> selected = List.of("user2", "user1");
        when(lottoTaskOrchestrator.execute(eq(TaskMode.BUY_AND_CHECK), eq(TriggerType.MANUAL), anyList()))
                .thenReturn(expected);

        BatchExecutionResult result = lottoService.execute(TaskMode.BUY_AND_CHECK, TriggerType.MANUAL, selected);

        assertThat(result).isEqualTo(expected);
        verify(lottoTaskOrchestrator).execute(eq(TaskMode.BUY_AND_CHECK), eq(TriggerType.MANUAL),
                eq(List.of(lottoProperties.getUsers().get(1), lottoProperties.getUsers().get(0))));
    }

    @Test
    void shouldThrowWhenInvalidUserIdIncluded() {
        assertThatThrownBy(() -> lottoService.execute(TaskMode.CHECK_ONLY, TriggerType.MANUAL, List.of("user1", "missing")))
                .isInstanceOf(InvalidManualUserSelectionException.class)
                .satisfies(exception -> {
                    InvalidManualUserSelectionException invalid = (InvalidManualUserSelectionException) exception;
                    assertThat(invalid.getInvalidUserIds()).containsExactly("missing");
                    assertThat(invalid.getAvailableUserIds()).containsExactly("user1", "user2", "user3");
                });
    }

    private LottoProperties.User createUser(String id) {
        LottoProperties.User user = new LottoProperties.User();
        user.setId(id);
        user.setPassword("password");
        user.setEmail(id + "@nowstart.org");
        user.setCount(1);
        user.setInit(false);
        return user;
    }

    private BatchExecutionResult createResult(int totalUsers, int successUsers, int failedUsers) {
        return new BatchExecutionResult(
                TaskMode.CHECK_ONLY,
                TriggerType.MANUAL,
                ExecutionStatus.fromCounts(totalUsers, failedUsers),
                Instant.parse("2026-02-23T00:00:00Z"),
                Instant.parse("2026-02-23T00:00:10Z"),
                10000,
                totalUsers,
                successUsers,
                failedUsers
        );
    }
}
