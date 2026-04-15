package org.nowstart.lotto.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.nowstart.lotto.application.dto.PurchaseAndCheckLottoCommand;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort;
import org.nowstart.lotto.application.port.out.LottoAutomationPort;
import org.nowstart.lotto.application.port.out.LottoAutomationSession;
import org.nowstart.lotto.application.port.out.SendNotificationPort;
import org.nowstart.lotto.domain.model.LottoAccountSnapshot;
import org.nowstart.lotto.domain.model.LottoExecution;
import org.nowstart.lotto.domain.model.LottoUser;
import org.nowstart.lotto.domain.model.NotificationMessage;
import org.nowstart.lotto.domain.type.ExecutionStatus;
import org.nowstart.lotto.domain.type.TaskMode;
import org.nowstart.lotto.domain.type.TriggerType;

@ExtendWith(MockitoExtension.class)
class PurchaseAndCheckLottoInteractorTest {

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

    private PurchaseAndCheckLottoInteractor purchaseAndCheckLottoInteractor;

    @BeforeEach
    void setUp() {
        LottoUserResolver lottoUserResolver = new LottoUserResolver(loadLottoUsersPort);
        LottoExecutionRunner lottoExecutionRunner = new LottoExecutionRunner(
                lottoUserResolver,
                lottoAutomationPort,
                sendNotificationPort,
                lottoNotificationFactory
        );
        purchaseAndCheckLottoInteractor = new PurchaseAndCheckLottoInteractor(lottoExecutionRunner);
    }

    @Test
    void shouldPurchaseAndReturnPartialFailureWhenOneUserFails() {
        LottoUser user1 = createUser("user1");
        LottoUser user2 = createUser("user2");
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
        when(lottoNotificationFactory.createSuccessMessage(any(), any(), any())).thenReturn(Optional.empty());
        when(lottoNotificationFactory.createFailureMessage(eq(user2), eq(TaskMode.BUY_AND_CHECK), any(Exception.class)))
                .thenReturn(failureMessage);

        LottoExecution result = purchaseAndCheckLottoInteractor.purchaseAndCheck(
                new PurchaseAndCheckLottoCommand(TriggerType.SCHEDULE, List.of("user1", "user2"))
        );

        assertThat(result.mode()).isEqualTo(TaskMode.BUY_AND_CHECK);
        assertThat(result.trigger()).isEqualTo(TriggerType.SCHEDULE);
        assertThat(result.status()).isEqualTo(ExecutionStatus.PARTIAL_FAILURE);
        assertThat(result.totalUsers()).isEqualTo(2);
        assertThat(result.successUsers()).isEqualTo(1);
        assertThat(result.failedUsers()).isEqualTo(1);

        verify(lottoAutomationPort).buy(session, user1);
        verify(sendNotificationPort, times(1)).send(failureMessage);
    }

    private LottoUser createUser(String id) {
        return new LottoUser(id, "password", 1, id + "@nowstart.org", false);
    }
}
