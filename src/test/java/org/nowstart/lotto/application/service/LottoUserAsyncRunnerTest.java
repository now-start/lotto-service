package org.nowstart.lotto.application.service;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.lang.reflect.Method;
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
import org.nowstart.lotto.application.port.out.LottoAutomationPort;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.CheckResult;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.LottoAccountSnapshot;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.LottoResult;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.PurchaseReceipt;
import org.nowstart.lotto.application.port.out.LottoAutomationSession;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort.LottoUser;
import org.nowstart.lotto.application.port.out.SendNotificationPort;
import org.nowstart.lotto.application.port.out.SendNotificationPort.NotificationMessage;
import org.nowstart.lotto.domain.type.TaskMode;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.scheduling.annotation.Async;

@ExtendWith(MockitoExtension.class)
@DisplayName("사용자별 로또 비동기 작업 실행")
class LottoUserAsyncRunnerTest {

    @Mock
    private LottoAutomationPort lottoAutomationPort;

    @Mock
    private SendNotificationPort sendNotificationPort;

    @Mock
    private LottoAutomationSession session;

    @Mock
    private LottoNotificationFactory lottoNotificationFactory;

    private LottoUserAsyncRunner lottoUserAsyncRunner;

    @BeforeEach
    void setUp() {
        lottoUserAsyncRunner = new LottoUserAsyncRunner(
                lottoAutomationPort,
                sendNotificationPort,
                lottoNotificationFactory
        );
    }

    @Test
    @DisplayName("사용자 작업 실행 메서드는 lottoTaskExecutor 기반 @Async로 선언한다")
    void shouldDeclareAsyncExecutor() throws NoSuchMethodException {
        // 준비: 사용자별 실행 메서드를 조회한다
        Method runAsync = LottoUserAsyncRunner.class.getMethod("runAsync", LottoUser.class, TaskMode.class, AtomicBoolean.class);

        // 실행 및 검증: Spring Async 프록시가 사용할 executor 이름을 명시한다
        Async async = runAsync.getAnnotation(Async.class);
        then(async).isNotNull();
        then(async.value()).isEqualTo("lottoTaskExecutor");
    }

    @Test
    @DisplayName("일반 확인이면 구매하지 않고 확인 결과 알림을 보낸다")
    void shouldCheckUserWithoutPurchase() {
        // 준비: 확인 결과 알림 생성이 가능하다
        LottoUser user = createUser("user1");
        NotificationMessage message = new NotificationMessage("subject", "text", null, "user1@nowstart.org");
        given(lottoAutomationPort.openSession()).willReturn(session);
        given(lottoAutomationPort.login(session, user)).willReturn(new LottoAccountSnapshot("ok", "1000"));
        given(lottoAutomationPort.check(session)).willReturn(List.of(createCheckResult()));
        given(lottoNotificationFactory.createCheckSuccessMessage(any(), any(), any()))
                .willReturn(Optional.of(message));

        // 실행: 사용자 확인 작업을 실행한다
        boolean result = lottoUserAsyncRunner.runAsync(user, TaskMode.CHECK, new AtomicBoolean(false)).join();

        // 검증: 구매 단계 없이 성공 알림을 전송한다
        then(result).isTrue();
        BDDMockito.then(lottoAutomationPort).should(never()).buy(eq(session), any(LottoUser.class));
        BDDMockito.then(sendNotificationPort).should(times(1)).send(message);
    }

    @Test
    @DisplayName("구매이면 구매 영수증으로 방금 산 복권만 확인한다")
    void shouldPurchaseAndCheckOnlyNewPurchaseResult() {
        // 준비: 사용자가 구매 가능하다
        LottoUser user = createUser("user1");
        PurchaseReceipt purchaseReceipt = new PurchaseReceipt(user.count(), LocalDate.of(2026, 5, 30));
        CheckResult newPurchaseResult = createCheckResult();
        NotificationMessage successMessage = new NotificationMessage("success", "numbers", null, "user1@nowstart.org");
        given(lottoAutomationPort.openSession()).willReturn(session);
        given(lottoAutomationPort.login(session, user)).willReturn(new LottoAccountSnapshot("ok", "5000"));
        given(lottoAutomationPort.buy(session, user)).willReturn(purchaseReceipt);
        given(lottoAutomationPort.check(session, purchaseReceipt)).willReturn(newPurchaseResult);
        given(lottoNotificationFactory.createCheckSuccessMessage(eq(user), any(), any()))
                .willReturn(Optional.of(successMessage));

        // 실행: 사용자 구매 작업을 실행한다
        boolean result = lottoUserAsyncRunner.runAsync(user, TaskMode.PURCHASE, new AtomicBoolean(false)).join();

        // 검증: 구매 이후 구매 전용 확인 경로를 타고 성공 알림을 전송한다
        then(result).isTrue();
        InOrder purchaseOrder = inOrder(lottoAutomationPort);
        BDDMockito.then(lottoAutomationPort).should(purchaseOrder).login(session, user);
        BDDMockito.then(lottoAutomationPort).should(purchaseOrder).buy(session, user);
        BDDMockito.then(lottoAutomationPort).should(purchaseOrder).check(session, purchaseReceipt);
        BDDMockito.then(lottoAutomationPort).should(never()).check(session);
        BDDMockito.then(sendNotificationPort).should(times(1)).send(successMessage);
    }

    @Test
    @DisplayName("사용자 작업 실패는 실패 알림을 보내고 false를 반환한다")
    void shouldSendFailureNotificationWhenUserTaskFails() {
        // 준비: 로그인 중 예외가 발생한다
        LottoUser user = createUser("user1");
        NotificationMessage failureMessage = new NotificationMessage("failure", "failed", null, "user1@nowstart.org");
        given(lottoAutomationPort.openSession()).willReturn(session);
        given(lottoAutomationPort.login(session, user)).willThrow(new IllegalStateException("login failed"));
        given(lottoNotificationFactory.createFailureMessage(eq(user), eq(TaskMode.PURCHASE), any(Exception.class)))
                .willReturn(failureMessage);

        // 실행: 사용자 구매 작업을 실행한다
        boolean result = lottoUserAsyncRunner.runAsync(user, TaskMode.PURCHASE, new AtomicBoolean(false)).join();

        // 검증: 실패 알림을 보내고 실패 결과를 반환한다
        then(result).isFalse();
        BDDMockito.then(sendNotificationPort).should(times(1)).send(failureMessage);
    }

    @Test
    @DisplayName("치명적 오류는 사용자 실패로 숨기지 않는다")
    void shouldPropagateFatalError() {
        // 준비: 세션 생성 중 치명적 오류가 발생한다
        LottoUser user = createUser("user1");
        OutOfMemoryError fatalError = new OutOfMemoryError("browser launch failed");
        given(lottoAutomationPort.openSession()).willThrow(fatalError);

        // 실행 및 검증: 치명적 오류는 호출자에게 전파한다
        thenThrownBy(() -> lottoUserAsyncRunner.runAsync(user, TaskMode.CHECK, new AtomicBoolean(false)).join())
                .isInstanceOf(OutOfMemoryError.class)
                .isSameAs(fatalError);
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
