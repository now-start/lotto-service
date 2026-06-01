package org.nowstart.lotto.application.service;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nowstart.lotto.application.port.in.LottoUseCase.TargetCommand;
import org.nowstart.lotto.application.port.in.LottoUseCase.LottoExecution;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort.LottoUser;
import org.nowstart.lotto.application.port.out.LottoAutomationPort;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.CheckResult;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.LottoAccountSnapshot;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.LottoResult;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.PurchaseReceipt;
import org.nowstart.lotto.application.port.out.LottoAutomationSession;
import org.nowstart.lotto.application.port.out.SendNotificationPort;
import org.nowstart.lotto.application.port.out.SendNotificationPort.NotificationMessage;
import org.nowstart.lotto.domain.exception.InvalidManualUserSelectionException;
import org.nowstart.lotto.domain.type.ExecutionStatus;
import org.nowstart.lotto.domain.type.TaskMode;
import org.nowstart.lotto.domain.type.TriggerType;

@ExtendWith(MockitoExtension.class)
@DisplayName("로또 작업 실행")
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
    @DisplayName("일반 확인이면 구매하지 않고 모든 사용자의 확인 결과 알림을 보낸다")
    void shouldCheckUsersWithoutPurchase() {
        // 준비: 두 명의 사용자가 있고 확인 결과 알림 생성이 가능하다
        LottoUser user1 = createUser("user1");
        LottoUser user2 = createUser("user2");
        NotificationMessage message = new NotificationMessage("subject", "text", null, "user1@nowstart.org");
        given(loadLottoUsersPort.loadUsers()).willReturn(List.of(user1, user2));
        given(lottoAutomationPort.openSession()).willReturn(session);
        given(lottoAutomationPort.login(eq(session), any(LottoUser.class)))
                .willReturn(new LottoAccountSnapshot("ok", "1000"));
        given(lottoAutomationPort.check(session)).willReturn(List.of(createCheckResult()));
        given(lottoNotificationFactory.createCheckSuccessMessage(any(), any(), any()))
                .willReturn(Optional.of(message));

        // 실행: 수동 확인 작업을 실행한다
        LottoExecution result = lottoInteractor.check(new TargetCommand(TriggerType.MANUAL, null));

        // 검증: 구매 단계 없이 두 사용자 모두 성공 처리되고 알림이 전송된다
        then(result.mode()).isEqualTo(TaskMode.CHECK);
        then(result.trigger()).isEqualTo(TriggerType.MANUAL);
        then(result.status()).isEqualTo(ExecutionStatus.SUCCESS);
        then(result.totalUsers()).isEqualTo(2);
        then(result.successUsers()).isEqualTo(2);
        then(result.failedUsers()).isZero();

        BDDMockito.then(lottoAutomationPort).should(never()).buy(eq(session), any(LottoUser.class));
        BDDMockito.then(sendNotificationPort).should(times(2)).send(message);
    }

    @Test
    @DisplayName("구매이면 구매 영수증으로 방금 산 복권만 확인하고 실패 사용자는 실패 알림을 보낸다")
    void shouldPurchaseAndCheckOnlyNewPurchaseResult() {
        // 준비: 첫 번째 사용자는 구매 가능하고 두 번째 사용자는 로그인에 실패한다
        LottoUser user1 = createUser("user1");
        LottoUser user2 = createUser("user2");
        PurchaseReceipt purchaseReceipt = new PurchaseReceipt(user1.count(), LocalDate.of(2026, 5, 30));
        CheckResult newPurchaseResult = createCheckResult();
        NotificationMessage successMessage = new NotificationMessage("success", "numbers", null, "user1@nowstart.org");
        NotificationMessage failureMessage = new NotificationMessage("failure", "failed", null, "user2@nowstart.org");
        given(loadLottoUsersPort.loadUsers()).willReturn(List.of(user1, user2));
        given(lottoAutomationPort.openSession()).willReturn(session);
        given(lottoAutomationPort.login(eq(session), any(LottoUser.class))).willAnswer(invocation -> {
            LottoUser user = invocation.getArgument(1);
            if ("user2".equals(user.id())) {
                throw new IllegalStateException("login failed");
            }
            return new LottoAccountSnapshot("ok", "5000");
        });
        given(lottoAutomationPort.buy(session, user1)).willReturn(purchaseReceipt);
        given(lottoAutomationPort.check(session, purchaseReceipt))
                .willReturn(newPurchaseResult);
        given(lottoNotificationFactory.createCheckSuccessMessage(eq(user1), any(), any()))
                .willReturn(Optional.of(successMessage));
        given(lottoNotificationFactory.createFailureMessage(eq(user2), eq(TaskMode.PURCHASE), any(Exception.class)))
                .willReturn(failureMessage);

        // 실행: 스케줄 구매 작업을 두 사용자에게 실행한다
        LottoExecution result = lottoInteractor.purchase(
                new TargetCommand(TriggerType.SCHEDULE, List.of("user1", "user2"))
        );

        // 검증: 구매 성공 사용자는 구매 이후 구매 전용 확인 경로를 타고 배치는 부분 실패로 끝난다
        then(result.mode()).isEqualTo(TaskMode.PURCHASE);
        then(result.trigger()).isEqualTo(TriggerType.SCHEDULE);
        then(result.status()).isEqualTo(ExecutionStatus.PARTIAL_FAILURE);
        then(result.totalUsers()).isEqualTo(2);
        then(result.successUsers()).isEqualTo(1);
        then(result.failedUsers()).isEqualTo(1);

        InOrder purchaseOrder = inOrder(lottoAutomationPort);
        BDDMockito.then(lottoAutomationPort).should(purchaseOrder).login(session, user1);
        BDDMockito.then(lottoAutomationPort).should(purchaseOrder).buy(session, user1);
        BDDMockito.then(lottoAutomationPort).should(purchaseOrder).check(session, purchaseReceipt);

        BDDMockito.then(lottoAutomationPort).should().buy(session, user1);
        BDDMockito.then(lottoAutomationPort).should(never()).check(session);
        BDDMockito.then(lottoAutomationPort).should().check(session, purchaseReceipt);
        BDDMockito.then(sendNotificationPort).should(times(1)).send(successMessage);
        BDDMockito.then(sendNotificationPort).should(times(1)).send(failureMessage);
    }

    @Test
    @DisplayName("요청한 사용자 ID가 설정에 없으면 예외를 던진다")
    void shouldThrowWhenInvalidUserIdIncluded() {
        // 준비: 설정에는 user1, user2만 존재한다
        given(loadLottoUsersPort.loadUsers()).willReturn(List.of(createUser("user1"), createUser("user2")));

        // 실행 및 검증: 존재하지 않는 사용자로 확인 작업을 요청하면 선택 오류가 발생한다
        thenThrownBy(() -> lottoInteractor.check(new TargetCommand(TriggerType.MANUAL, List.of("missing"))))
                .isInstanceOf(InvalidManualUserSelectionException.class)
                .satisfies(exception -> {
                    InvalidManualUserSelectionException invalid = (InvalidManualUserSelectionException) exception;
                    then(invalid.getInvalidUserIds()).containsExactly("missing");
                    then(invalid.getAvailableUserIds()).containsExactly("user1", "user2");
                });
    }

    private LottoUser createUser(String id) {
        return new LottoUser(id, "password", 1, id + "@nowstart.org", false);
    }

    private CheckResult createCheckResult() {
        return new CheckResult(
                new LottoResult("2026-01-01", "1000", "로또", "1,2,3,4,5,6", "1", "당첨", "5000"),
                new byte[] {1}
        );
    }
}
