package org.nowstart.lotto.application.service;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.nowstart.lotto.application.dto.CheckLottoResultsCommand;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort;
import org.nowstart.lotto.application.port.out.LottoAutomationPort;
import org.nowstart.lotto.application.port.out.LottoAutomationSession;
import org.nowstart.lotto.application.port.out.SendNotificationPort;
import org.nowstart.lotto.domain.model.LottoAccountSnapshot;
import org.nowstart.lotto.domain.model.LottoExecution;
import org.nowstart.lotto.domain.model.LottoResult;
import org.nowstart.lotto.domain.model.LottoUser;
import org.nowstart.lotto.domain.model.NotificationMessage;
import org.nowstart.lotto.domain.type.ExecutionStatus;
import org.nowstart.lotto.domain.type.TaskMode;
import org.nowstart.lotto.domain.type.TriggerType;

@ExtendWith(MockitoExtension.class)
class CheckLottoResultsInteractorTest {

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

    private CheckLottoResultsInteractor checkLottoResultsInteractor;

    @BeforeEach
    void setUp() {
        LottoUserResolver lottoUserResolver = new LottoUserResolver(loadLottoUsersPort);
        LottoExecutionRunner lottoExecutionRunner = new LottoExecutionRunner(
                lottoUserResolver,
                lottoAutomationPort,
                sendNotificationPort,
                lottoNotificationFactory
        );
        checkLottoResultsInteractor = new CheckLottoResultsInteractor(lottoExecutionRunner);
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
        when(lottoAutomationPort.check(session)).thenReturn(List.of(createResult()));
        when(lottoNotificationFactory.createSuccessMessage(any(), any(), any()))
                .thenReturn(Optional.of(message));

        LottoExecution result = checkLottoResultsInteractor.check(
                new CheckLottoResultsCommand(TriggerType.MANUAL, null)
        );

        assertThat(result.mode()).isEqualTo(TaskMode.CHECK_ONLY);
        assertThat(result.trigger()).isEqualTo(TriggerType.MANUAL);
        assertThat(result.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(result.totalUsers()).isEqualTo(2);
        assertThat(result.successUsers()).isEqualTo(2);
        assertThat(result.failedUsers()).isZero();

        verify(lottoAutomationPort, never()).buy(eq(session), any(LottoUser.class));
        verify(sendNotificationPort, times(2)).send(message);
    }

    private LottoUser createUser(String id) {
        return new LottoUser(id, "password", 1, id + "@nowstart.org", false);
    }

    private LottoResult createResult() {
        return new LottoResult("2026-01-01", "1000", "로또", "1,2,3,4,5,6", "1", "당첨", "5000", null);
    }
}
