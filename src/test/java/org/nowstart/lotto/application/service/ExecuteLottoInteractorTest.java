package org.nowstart.lotto.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nowstart.lotto.application.dto.ExecuteLottoCommand;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort;
import org.nowstart.lotto.application.port.out.LottoAutomationPort;
import org.nowstart.lotto.application.port.out.LottoAutomationSession;
import org.nowstart.lotto.application.port.out.SendNotificationPort;
import org.nowstart.lotto.domain.exception.InvalidManualUserSelectionException;
import org.nowstart.lotto.domain.model.LottoAccountSnapshot;
import org.nowstart.lotto.domain.model.LottoExecution;
import org.nowstart.lotto.domain.model.LottoResult;
import org.nowstart.lotto.domain.model.LottoUser;
import org.nowstart.lotto.domain.model.NotificationMessage;
import org.nowstart.lotto.domain.type.ExecutionStatus;
import org.nowstart.lotto.domain.type.TaskMode;
import org.nowstart.lotto.domain.type.TriggerType;

@ExtendWith(MockitoExtension.class)
class ExecuteLottoInteractorTest {

    @Mock
    private LoadLottoUsersPort loadLottoUsersPort;

    @Mock
    private LottoAutomationPort lottoAutomationPort;

    @Mock
    private SendNotificationPort sendNotificationPort;

    @Mock
    private LottoAutomationSession session;

    @Mock
    private LottoNotificationFactory lottoNotificationFactory;

    private ExecuteLottoInteractor executeLottoInteractor;
    private List<LottoUser> users;

    @BeforeEach
    void setUp() {
        users = List.of(
                createUser("user1"),
                createUser("user2"),
                createUser("user3")
        );

        executeLottoInteractor = new ExecuteLottoInteractor(
                loadLottoUsersPort,
                lottoAutomationPort,
                sendNotificationPort,
                lottoNotificationFactory
        );
    }

    @Test
    void shouldExecuteAllUsersWhenUserIdsMissing() {
        NotificationMessage message = new NotificationMessage("subject", "text", null, "user1@nowstart.org");
        when(loadLottoUsersPort.loadUsers()).thenReturn(users);
        when(lottoAutomationPort.openSession()).thenReturn(session);
        when(lottoAutomationPort.login(eq(session), any(LottoUser.class)))
                .thenReturn(new LottoAccountSnapshot("ok", "1000"));
        when(lottoAutomationPort.check(session)).thenReturn(List.of());
        when(lottoNotificationFactory.createSuccessMessage(any(), any(), any())).thenReturn(Optional.empty());

        LottoExecution result = executeLottoInteractor.execute(
                new ExecuteLottoCommand(TaskMode.CHECK_ONLY, TriggerType.MANUAL, null)
        );

        assertThat(result.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(result.totalUsers()).isEqualTo(3);
        assertThat(result.successUsers()).isEqualTo(3);
        assertThat(result.failedUsers()).isZero();

        verify(lottoAutomationPort, never()).buy(session, users.getFirst());
        verify(sendNotificationPort, never()).send(message);
    }

    @Test
    void shouldExecuteSelectedUsersOnly() {
        NotificationMessage message = new NotificationMessage("subject", "text", null, "user1@nowstart.org");
        when(loadLottoUsersPort.loadUsers()).thenReturn(users);
        when(lottoAutomationPort.openSession()).thenReturn(session);
        when(lottoAutomationPort.login(eq(session), any(LottoUser.class)))
                .thenReturn(new LottoAccountSnapshot("ok", "1000"));
        when(lottoAutomationPort.check(session)).thenReturn(List.of(createResult()));
        when(lottoNotificationFactory.createSuccessMessage(any(), any(), any())).thenReturn(Optional.of(message));

        LottoExecution result = executeLottoInteractor.execute(
                new ExecuteLottoCommand(TaskMode.BUY_AND_CHECK, TriggerType.MANUAL, List.of("user2", "user1"))
        );

        assertThat(result.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(result.totalUsers()).isEqualTo(2);
        assertThat(result.successUsers()).isEqualTo(2);
        assertThat(result.failedUsers()).isZero();

        verify(lottoAutomationPort).buy(session, users.get(1));
        verify(lottoAutomationPort).buy(session, users.get(0));
        verify(lottoAutomationPort, never()).buy(session, users.get(2));
        verify(sendNotificationPort, times(2)).send(message);
    }

    @Test
    void shouldThrowWhenInvalidUserIdIncluded() {
        when(loadLottoUsersPort.loadUsers()).thenReturn(users);

        assertThatThrownBy(() -> executeLottoInteractor.execute(
                new ExecuteLottoCommand(TaskMode.CHECK_ONLY, TriggerType.MANUAL, List.of("user1", "missing"))
        ))
                .isInstanceOf(InvalidManualUserSelectionException.class)
                .satisfies(exception -> {
                    InvalidManualUserSelectionException invalid = (InvalidManualUserSelectionException) exception;
                    assertThat(invalid.getInvalidUserIds()).containsExactly("missing");
                    assertThat(invalid.getAvailableUserIds()).containsExactly("user1", "user2", "user3");
                });
    }

    @Test
    void shouldReturnPartialFailureWhenSomeUsersFail() {
        NotificationMessage failureMessage = new NotificationMessage("failure", "failed", null, "user2@nowstart.org");
        when(loadLottoUsersPort.loadUsers()).thenReturn(users);
        when(lottoAutomationPort.openSession()).thenReturn(session);
        when(lottoAutomationPort.login(eq(session), any(LottoUser.class))).thenAnswer(invocation -> {
            LottoUser user = invocation.getArgument(1);
            if ("user2".equals(user.id())) {
                throw new IllegalStateException("login failed");
            }
            return new LottoAccountSnapshot("ok", "5000");
        });
        when(lottoAutomationPort.check(session)).thenReturn(List.of());
        when(lottoNotificationFactory.createSuccessMessage(any(), any(), any())).thenReturn(Optional.empty());
        when(lottoNotificationFactory.createFailureMessage(eq(users.get(1)), eq(TaskMode.CHECK_ONLY), any(Exception.class)))
                .thenReturn(failureMessage);

        LottoExecution result = executeLottoInteractor.execute(
                new ExecuteLottoCommand(TaskMode.CHECK_ONLY, TriggerType.SCHEDULE, null)
        );

        assertThat(result.status()).isEqualTo(ExecutionStatus.PARTIAL_FAILURE);
        assertThat(result.totalUsers()).isEqualTo(3);
        assertThat(result.successUsers()).isEqualTo(2);
        assertThat(result.failedUsers()).isEqualTo(1);

        verify(sendNotificationPort).send(failureMessage);
    }

    private LottoUser createUser(String id) {
        return new LottoUser(id, "password", 1, id + "@nowstart.org", false);
    }

    private LottoResult createResult() {
        return new LottoResult("2026-01-01", "1000", "로또", "1,2,3,4,5,6", "1", "당첨", "5000", null);
    }
}
