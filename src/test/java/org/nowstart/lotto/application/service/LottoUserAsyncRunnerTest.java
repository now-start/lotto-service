package org.nowstart.lotto.application.service;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort.LottoUser;
import org.nowstart.lotto.application.port.out.LottoAutomationPort;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.CheckResult;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.LottoAccountSnapshot;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.LottoResult;
import org.nowstart.lotto.application.port.out.LottoAutomationSession;
import org.nowstart.lotto.application.port.out.SendNotificationPort;
import org.nowstart.lotto.application.port.out.SendNotificationPort.NotificationMessage;
import org.nowstart.lotto.domain.ledger.PurchasePlan;
import org.nowstart.lotto.domain.type.TaskMode;
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

    @Mock
    private LottoPurchaseProcedure lottoPurchaseProcedure;

    private LottoUserAsyncRunner lottoUserAsyncRunner;

    @BeforeEach
    void setUp() {
        lottoUserAsyncRunner = new LottoUserAsyncRunner(
                lottoAutomationPort,
                sendNotificationPort,
                lottoNotificationFactory,
                lottoPurchaseProcedure
        );
    }

    @Test
    @DisplayName("사용자 작업 실행 메서드는 lottoTaskExecutor 기반 @Async로 선언한다")
    void shouldDeclareAsyncExecutor() throws NoSuchMethodException {
        Method runAsync = LottoUserAsyncRunner.class.getMethod("runAsync", LottoUser.class, TaskMode.class, AtomicBoolean.class);

        Async async = runAsync.getAnnotation(Async.class);
        then(async).isNotNull();
        then(async.value()).isEqualTo("lottoTaskExecutor");
    }

    @Test
    @DisplayName("일반 확인이면 구매하지 않고 확인 결과 알림을 보낸다")
    void shouldCheckUserWithoutPurchase() {
        LottoUser user = createUser("user1");
        NotificationMessage message = new NotificationMessage("subject", "text", null, "user1@nowstart.org");
        given(lottoAutomationPort.openSession()).willReturn(session);
        given(lottoAutomationPort.login(session, user)).willReturn(new LottoAccountSnapshot("ok", "1000"));
        given(lottoAutomationPort.check(session)).willReturn(List.of(createCheckResult()));
        given(lottoNotificationFactory.createCheckSuccessMessage(any(), any(), any()))
                .willReturn(Optional.of(message));

        boolean result = lottoUserAsyncRunner.runAsync(user, TaskMode.CHECK, new AtomicBoolean(false)).join();

        then(result).isTrue();
        BDDMockito.then(lottoPurchaseProcedure).shouldHaveNoInteractions();
        BDDMockito.then(lottoAutomationPort).should(never()).buy(any(), any(), anyInt(), any());
        BDDMockito.then(sendNotificationPort).should(times(1)).send(message);
    }

    @Test
    @DisplayName("구매이면 로그인 후 구매 절차를 실행하고 결과 통지를 보낸다")
    void shouldRunPurchaseProcedureAndNotifyOutcome() {
        LottoUser user = createUser("user1");
        LottoAccountSnapshot account = new LottoAccountSnapshot("ok", "5000");
        PurchaseOutcome outcome = new PurchaseOutcome.Purchased(PurchasePlan.of(1252, 1, 0), List.of(createCheckResult()), null);
        NotificationMessage successMessage = new NotificationMessage("success", "numbers", null, "user1@nowstart.org");
        given(lottoPurchaseProcedure.precheck(user)).willReturn(Optional.empty());
        given(lottoAutomationPort.openSession()).willReturn(session);
        given(lottoAutomationPort.login(session, user)).willReturn(account);
        given(lottoPurchaseProcedure.purchase(eq(session), eq(user), eq(account), any())).willReturn(outcome);
        given(lottoNotificationFactory.createPurchaseOutcomeMessage(user, account, outcome))
                .willReturn(Optional.of(successMessage));

        boolean result = lottoUserAsyncRunner.runAsync(user, TaskMode.PURCHASE, new AtomicBoolean(false)).join();

        then(result).isTrue();
        InOrder purchaseOrder = inOrder(lottoAutomationPort, lottoPurchaseProcedure);
        BDDMockito.then(lottoAutomationPort).should(purchaseOrder).login(session, user);
        BDDMockito.then(lottoPurchaseProcedure).should(purchaseOrder).purchase(eq(session), eq(user), eq(account), any());
        BDDMockito.then(lottoAutomationPort).should(never()).check(session);
        BDDMockito.then(sendNotificationPort).should(times(1)).send(successMessage);
        BDDMockito.then(session).should(never()).markFailed();
    }

    @Test
    @DisplayName("판매 시간이 아니면 브라우저를 열지 않고 안내만 보낸다")
    void shouldNotOpenBrowserOutsideSalesHours() {
        LottoUser user = createUser("user1");
        PurchaseOutcome notOnSale = new PurchaseOutcome.NotOnSale(ZonedDateTime.parse("2026-11-29T06:00+09:00[Asia/Seoul]"), 1253);
        NotificationMessage notice = new NotificationMessage("notice", "text", null, "user1@nowstart.org");
        given(lottoPurchaseProcedure.precheck(user)).willReturn(Optional.of(notOnSale));
        given(lottoNotificationFactory.createPurchaseOutcomeMessage(user, null, notOnSale)).willReturn(Optional.of(notice));

        boolean result = lottoUserAsyncRunner.runAsync(user, TaskMode.PURCHASE, new AtomicBoolean(false)).join();

        then(result).isFalse();
        BDDMockito.then(lottoAutomationPort).shouldHaveNoInteractions();
        BDDMockito.then(sendNotificationPort).should().send(notice);
    }

    @Test
    @DisplayName("구매 판정 불가면 진단 자료를 남기고 '수동 구매 금지' 안내를 보낸다")
    void shouldKeepDiagnosticsWhenPurchaseIsUncertain() {
        LottoUser user = createUser("user1");
        LottoAccountSnapshot account = new LottoAccountSnapshot("ok", "5000");
        PurchaseOutcome uncertain = new PurchaseOutcome.Uncertain(PurchasePlan.of(1252, 1, 0), Instant.parse("2026-11-23T02:40:00Z"));
        NotificationMessage notice = new NotificationMessage("uncertain", "text", null, "user1@nowstart.org");
        var trace = new SendNotificationPort.Attachment("lotto-trace.zip", new byte[] {9});
        given(lottoPurchaseProcedure.precheck(user)).willReturn(Optional.empty());
        given(lottoAutomationPort.openSession()).willReturn(session);
        given(lottoAutomationPort.login(session, user)).willReturn(account);
        given(lottoPurchaseProcedure.purchase(eq(session), eq(user), eq(account), any())).willReturn(uncertain);
        given(lottoNotificationFactory.createPurchaseOutcomeMessage(user, account, uncertain)).willReturn(Optional.of(notice));
        given(session.failureTrace()).willReturn(trace);

        boolean result = lottoUserAsyncRunner.runAsync(user, TaskMode.PURCHASE, new AtomicBoolean(false)).join();

        then(result).isFalse();
        BDDMockito.then(session).should().markFailed();
        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        BDDMockito.then(sendNotificationPort).should().send(captor.capture());
        then(captor.getValue().attachment()).isSameAs(trace);
    }

    @Test
    @DisplayName("사용자 작업 실패는 실패 알림을 보내고 false를 반환한다")
    void shouldSendFailureNotificationWhenUserTaskFails() {
        LottoUser user = createUser("user1");
        NotificationMessage failureMessage = new NotificationMessage("failure", "failed", null, "user1@nowstart.org");
        given(lottoPurchaseProcedure.precheck(user)).willReturn(Optional.empty());
        given(lottoAutomationPort.openSession()).willReturn(session);
        given(lottoAutomationPort.login(session, user)).willThrow(new IllegalStateException("login failed"));
        given(lottoNotificationFactory.createFailureMessage(eq(user), eq(TaskMode.PURCHASE), any(Exception.class)))
                .willReturn(failureMessage);

        boolean result = lottoUserAsyncRunner.runAsync(user, TaskMode.PURCHASE, new AtomicBoolean(false)).join();

        then(result).isFalse();
        BDDMockito.then(sendNotificationPort).should(times(1)).send(failureMessage);
    }

    @Test
    @DisplayName("치명적 오류는 사용자 실패로 숨기지 않는다")
    void shouldPropagateFatalError() {
        LottoUser user = createUser("user1");
        OutOfMemoryError fatalError = new OutOfMemoryError("browser launch failed");
        given(lottoAutomationPort.openSession()).willThrow(fatalError);

        thenThrownBy(() -> lottoUserAsyncRunner.runAsync(user, TaskMode.CHECK, new AtomicBoolean(false)).join())
                .isInstanceOf(OutOfMemoryError.class)
                .isSameAs(fatalError);
    }

    @Test
    @DisplayName("실패한 세션을 닫은 뒤 진단 자료를 실패 알림에 첨부한다")
    void shouldAttachTraceAfterClosingFailedSession() {
        LottoUser user = createUser("user1");
        NotificationMessage message = new NotificationMessage("failure", "text", null, "user1@nowstart.org");
        var attachment = new SendNotificationPort.Attachment("lotto-trace-user1.zip", new byte[] {1, 2, 3});
        given(lottoPurchaseProcedure.precheck(user)).willReturn(Optional.empty());
        given(lottoAutomationPort.openSession()).willReturn(session);
        given(lottoAutomationPort.login(session, user)).willThrow(new IllegalStateException("failed"));
        given(lottoNotificationFactory.createFailureMessage(eq(user), eq(TaskMode.PURCHASE), any(Exception.class)))
                .willReturn(message);
        org.mockito.Mockito.doAnswer(invocation -> {
            given(session.failureTrace()).willReturn(attachment);
            return null;
        }).when(session).close();

        then(lottoUserAsyncRunner.runAsync(user, TaskMode.PURCHASE, new AtomicBoolean(false)).join()).isFalse();

        var captor = ArgumentCaptor.forClass(NotificationMessage.class);
        InOrder order = inOrder(session, sendNotificationPort);
        order.verify(session).markFailed();
        order.verify(session).close();
        order.verify(sendNotificationPort).send(captor.capture());
        then(captor.getValue().attachment()).isSameAs(attachment);
        then(captor.getValue().attachment().content()).containsExactly((byte) 1, (byte) 2, (byte) 3);
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
