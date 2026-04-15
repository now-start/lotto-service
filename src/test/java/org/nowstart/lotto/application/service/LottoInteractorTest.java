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
import org.nowstart.lotto.application.dto.CheckLottoCommand;
import org.nowstart.lotto.application.dto.PurchaseLottoCommand;
import org.nowstart.lotto.application.model.LottoCheckResult;
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
class LottoInteractorTest {

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

    private LottoInteractor lottoInteractor;

    @BeforeEach
    void setUp() {
        lottoInteractor = new LottoInteractor(
                loadLottoUsersPort,
                lottoAutomationPort,
                sendNotificationPort,
                lottoNotificationFactory
        );
    }

    @Test
    void shouldCheckUsersWithoutPurchase() {
        LottoUser user1 = createUser("user1");
        LottoUser user2 = createUser("user2");
        NotificationMessage message = new NotificationMessage("subject", "text", null, "user1@nowstart.org");
        when(loadLottoUsersPort.loadUsers()).thenReturn(List.of(user1, user2));
        when(lottoAutomationPort.openSession()).thenReturn(session);
        when(lottoAutomationPort.login(eq(session), any(LottoUser.class)))
                .thenReturn(new LottoAccountSnapshot("ok", "1000"));
        when(lottoAutomationPort.check(session)).thenReturn(List.of(createCheckResult()));
        when(lottoNotificationFactory.createCheckSuccessMessage(any(), any(), any()))
                .thenReturn(Optional.of(message));

        LottoExecution result = lottoInteractor.check(new CheckLottoCommand(TriggerType.MANUAL, null));

        assertThat(result.mode()).isEqualTo(TaskMode.CHECK);
        assertThat(result.trigger()).isEqualTo(TriggerType.MANUAL);
        assertThat(result.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(result.totalUsers()).isEqualTo(2);
        assertThat(result.successUsers()).isEqualTo(2);
        assertThat(result.failedUsers()).isZero();

        verify(lottoAutomationPort, never()).buy(eq(session), any(LottoUser.class));
        verify(sendNotificationPort, times(2)).send(message);
    }

    @Test
    void shouldPurchaseWithoutCheckingAndReturnPartialFailureWhenOneUserFails() {
        LottoUser user1 = createUser("user1");
        LottoUser user2 = createUser("user2");
        NotificationMessage successMessage = new NotificationMessage("success", "numbers", null, "user1@nowstart.org");
        NotificationMessage failureMessage = new NotificationMessage("failure", "failed", null, "user2@nowstart.org");
        when(loadLottoUsersPort.loadUsers()).thenReturn(List.of(user1, user2));
        when(lottoAutomationPort.openSession()).thenReturn(session);
        when(lottoAutomationPort.login(eq(session), any(LottoUser.class))).thenAnswer(invocation -> {
            LottoUser user = invocation.getArgument(1);
            if ("user2".equals(user.id())) {
                throw new IllegalStateException("login failed");
            }
            return new LottoAccountSnapshot("ok", "5000");
        });
        when(lottoAutomationPort.check(session)).thenReturn(List.of(createCheckResult()));
        when(lottoNotificationFactory.createCheckSuccessMessage(eq(user1), any(), any()))
                .thenReturn(Optional.of(successMessage));
        when(lottoNotificationFactory.createFailureMessage(eq(user2), eq(TaskMode.PURCHASE), any(Exception.class)))
                .thenReturn(failureMessage);

        LottoExecution result = lottoInteractor.purchase(
                new PurchaseLottoCommand(TriggerType.SCHEDULE, List.of("user1", "user2"))
        );

        assertThat(result.mode()).isEqualTo(TaskMode.PURCHASE);
        assertThat(result.trigger()).isEqualTo(TriggerType.SCHEDULE);
        assertThat(result.status()).isEqualTo(ExecutionStatus.PARTIAL_FAILURE);
        assertThat(result.totalUsers()).isEqualTo(2);
        assertThat(result.successUsers()).isEqualTo(1);
        assertThat(result.failedUsers()).isEqualTo(1);

        verify(lottoAutomationPort).buy(session, user1);
        verify(lottoAutomationPort, times(1)).check(session);
        verify(sendNotificationPort, times(1)).send(successMessage);
        verify(sendNotificationPort, times(1)).send(failureMessage);
    }

    @Test
    void shouldThrowWhenInvalidUserIdIncluded() {
        when(loadLottoUsersPort.loadUsers()).thenReturn(List.of(createUser("user1"), createUser("user2")));

        assertThatThrownBy(() -> lottoInteractor.check(new CheckLottoCommand(TriggerType.MANUAL, List.of("missing"))))
                .isInstanceOf(InvalidManualUserSelectionException.class)
                .satisfies(exception -> {
                    InvalidManualUserSelectionException invalid = (InvalidManualUserSelectionException) exception;
                    assertThat(invalid.getInvalidUserIds()).containsExactly("missing");
                    assertThat(invalid.getAvailableUserIds()).containsExactly("user1", "user2");
                });
    }

    private LottoUser createUser(String id) {
        return new LottoUser(id, "password", 1, id + "@nowstart.org", false);
    }

    private LottoCheckResult createCheckResult() {
        return new LottoCheckResult(
                new LottoResult("2026-01-01", "1000", "로또", "1,2,3,4,5,6", "1", "당첨", "5000"),
                new byte[] {1}
        );
    }
}
