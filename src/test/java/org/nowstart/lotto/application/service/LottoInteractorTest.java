package org.nowstart.lotto.application.service;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nowstart.lotto.application.port.in.LottoUseCase.LottoExecution;
import org.nowstart.lotto.application.port.in.LottoUseCase.TargetCommand;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort;
import org.nowstart.lotto.config.LottoProperties;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort.LottoUser;
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
    private LottoUserRunner lottoUserRunner;

    private LottoInteractor lottoInteractor;

    @BeforeEach
    void setUp() {
        lottoInteractor = new LottoInteractor(loadLottoUsersPort, lottoUserRunner, new LottoProperties());
    }

    @Test
    @DisplayName("일반 확인이면 모든 사용자를 비동기 실행하고 성공 결과를 집계한다")
    void shouldCheckUsersAsynchronouslyAndAggregateSuccess() {
        // 준비: 두 사용자 작업이 모두 성공한다
        LottoUser user1 = createUser("user1");
        LottoUser user2 = createUser("user2");
        given(loadLottoUsersPort.loadUsers()).willReturn(List.of(user1, user2));
        given(lottoUserRunner.runAsync(user1, TaskMode.CHECK)).willReturn(CompletableFuture.completedFuture(true));
        given(lottoUserRunner.runAsync(user2, TaskMode.CHECK)).willReturn(CompletableFuture.completedFuture(true));

        // 실행: 수동 확인 작업을 실행한다
        LottoExecution result = lottoInteractor.check(new TargetCommand(TriggerType.MANUAL, null));

        // 검증: 모든 사용자 작업을 요청하고 성공으로 집계한다
        then(result.mode()).isEqualTo(TaskMode.CHECK);
        then(result.trigger()).isEqualTo(TriggerType.MANUAL);
        then(result.status()).isEqualTo(ExecutionStatus.SUCCESS);
        then(result.totalUsers()).isEqualTo(2);
        then(result.successUsers()).isEqualTo(2);
        then(result.failedUsers()).isZero();
        BDDMockito.then(lottoUserRunner).should().runAsync(user1, TaskMode.CHECK);
        BDDMockito.then(lottoUserRunner).should().runAsync(user2, TaskMode.CHECK);
    }

    @Test
    @DisplayName("구매이면 요청 사용자만 실행하고 성공/실패 결과를 집계한다")
    void shouldPurchaseRequestedUsersAndAggregatePartialFailure() {
        // 준비: 첫 번째 사용자는 성공하고 두 번째 사용자는 실패한다
        LottoUser user1 = createUser("user1");
        LottoUser user2 = createUser("user2");
        given(loadLottoUsersPort.loadUsers()).willReturn(List.of(user1, user2));
        given(lottoUserRunner.runAsync(user1, TaskMode.PURCHASE)).willReturn(CompletableFuture.completedFuture(true));
        given(lottoUserRunner.runAsync(user2, TaskMode.PURCHASE)).willReturn(CompletableFuture.completedFuture(false));

        // 실행: 스케줄 구매 작업을 두 사용자에게 실행한다
        LottoExecution result = lottoInteractor.purchase(
                new TargetCommand(TriggerType.SCHEDULE, List.of("user1", "user2"))
        );

        // 검증: 구매 작업 결과를 부분 실패로 집계한다
        then(result.mode()).isEqualTo(TaskMode.PURCHASE);
        then(result.trigger()).isEqualTo(TriggerType.SCHEDULE);
        then(result.status()).isEqualTo(ExecutionStatus.PARTIAL_FAILURE);
        then(result.totalUsers()).isEqualTo(2);
        then(result.successUsers()).isEqualTo(1);
        then(result.failedUsers()).isEqualTo(1);
    }

    @Test
    @DisplayName("비동기 작업의 치명적 오류는 사용자 실패로 숨기지 않는다")
    void shouldPropagateFatalAsyncFailure() {
        // 준비: 사용자 작업 중 일반 예외가 아닌 치명적 오류가 발생한다
        LottoUser user = createUser("user1");
        OutOfMemoryError fatalError = new OutOfMemoryError("browser launch failed");
        CompletableFuture<Boolean> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(fatalError);
        given(loadLottoUsersPort.loadUsers()).willReturn(List.of(user));
        given(lottoUserRunner.runAsync(user, TaskMode.CHECK)).willReturn(failedFuture);

        // 실행 및 검증: 치명적 오류는 실패 카운트로 숨기지 않고 호출자에게 전파한다
        thenThrownBy(() -> lottoInteractor.check(new TargetCommand(TriggerType.MANUAL, null)))
                .isSameAs(fatalError);
    }

    @Test
    @DisplayName("치명적 오류가 발생하면 남은 비동기 작업을 취소한다")
    void shouldCancelRemainingTasksWhenFatalAsyncFailureOccurs() {
        // 준비: 첫 번째 사용자 작업은 치명적 오류가 발생하고 두 번째 사용자 작업은 아직 대기 중이다
        LottoUser user1 = createUser("user1");
        LottoUser user2 = createUser("user2");
        OutOfMemoryError fatalError = new OutOfMemoryError("browser launch failed");
        CompletableFuture<Boolean> fatalFuture = new CompletableFuture<>();
        CompletableFuture<Boolean> waitingFuture = new CompletableFuture<>();
        fatalFuture.completeExceptionally(fatalError);
        given(loadLottoUsersPort.loadUsers()).willReturn(List.of(user1, user2));
        given(lottoUserRunner.runAsync(user1, TaskMode.CHECK)).willReturn(fatalFuture);
        given(lottoUserRunner.runAsync(user2, TaskMode.CHECK)).willReturn(waitingFuture);

        // 실행 및 검증: 치명적 오류를 전파하기 전 남은 작업을 best-effort로 취소한다
        thenThrownBy(() -> lottoInteractor.check(new TargetCommand(TriggerType.MANUAL, null)))
                .isSameAs(fatalError);
        then(waitingFuture).isCancelled();
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
}
