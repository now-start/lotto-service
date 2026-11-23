package org.nowstart.lotto.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.CompletableFuture
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.nowstart.lotto.application.exception.InvalidManualUserSelectionException
import org.nowstart.lotto.application.port.inbound.TargetCommand
import org.nowstart.lotto.application.port.outbound.LoadLottoUsersPort
import org.nowstart.lotto.application.port.outbound.LottoUser
import org.nowstart.lotto.domain.type.ExecutionStatus
import org.nowstart.lotto.domain.type.TaskMode
import org.nowstart.lotto.domain.type.TriggerType

class LottoInteractorTest {

    private val usersPort = mockk<LoadLottoUsersPort>()
    private val runner = mockk<LottoUserRunner>()
    private val interactor = LottoInteractor(usersPort, runner, ExecutionSettings(userTaskTimeoutMs = 180_000))
    private val user1 = user("user1")
    private val user2 = user("user2")

    @Test
    fun `일반 확인이면 모든 사용자를 비동기 실행하고 성공 결과를 집계한다`() {
        every { usersPort.loadUsers() } returns listOf(user1, user2)
        every { runner.runAsync(any(), TaskMode.CHECK, any()) } returns CompletableFuture.completedFuture(true)

        val result = interactor.check(TargetCommand(TriggerType.MANUAL))

        assertThat(result.mode).isEqualTo(TaskMode.CHECK)
        assertThat(result.trigger).isEqualTo(TriggerType.MANUAL)
        assertThat(result.status).isEqualTo(ExecutionStatus.SUCCESS)
        assertThat(result.totalUsers).isEqualTo(2)
        assertThat(result.successUsers).isEqualTo(2)
        assertThat(result.failedUsers).isZero()
        verify { runner.runAsync(user1, TaskMode.CHECK, any()) }
        verify { runner.runAsync(user2, TaskMode.CHECK, any()) }
    }

    @Test
    fun `구매이면 요청 사용자만 실행하고 성공 실패 결과를 집계한다`() {
        every { usersPort.loadUsers() } returns listOf(user1, user2)
        every { runner.runAsync(user1, TaskMode.PURCHASE, any()) } returns CompletableFuture.completedFuture(true)
        every { runner.runAsync(user2, TaskMode.PURCHASE, any()) } returns CompletableFuture.completedFuture(false)

        val result = interactor.purchase(TargetCommand(TriggerType.SCHEDULE, listOf("user1", "user2")))

        assertThat(result.status).isEqualTo(ExecutionStatus.PARTIAL_FAILURE)
        assertThat(result.totalUsers).isEqualTo(2)
        assertThat(result.successUsers).isEqualTo(1)
        assertThat(result.failedUsers).isEqualTo(1)
    }

    @Test
    fun `같은 계정의 확인 작업이 아직 끝나지 않았으면 구매를 시작하지 않는다`() {
        val shortTimeout = LottoInteractor(usersPort, runner, ExecutionSettings(userTaskTimeoutMs = 1_000))
        val runningCheck = CompletableFuture<Boolean>()
        every { usersPort.loadUsers() } returns listOf(user1)
        every { runner.runAsync(user1, TaskMode.CHECK, any()) } returns runningCheck

        // 확인 작업은 호출자 타임아웃(1초)으로 먼저 반환되지만 실제 작업은 아직 진행 중이다
        val check = shortTimeout.check(TargetCommand.all(TriggerType.MANUAL))
        val purchase = shortTimeout.purchase(TargetCommand.all(TriggerType.MANUAL))

        assertThat(check.status).isEqualTo(ExecutionStatus.FAILURE)
        assertThat(purchase.status).isEqualTo(ExecutionStatus.FAILURE)
        verify(exactly = 0) { runner.runAsync(any(), TaskMode.PURCHASE, any()) }
        runningCheck.complete(true)
    }

    @Test
    fun `비동기 작업의 치명적 오류는 숨기지 않고 남은 작업을 취소한다`() {
        val fatal = OutOfMemoryError("browser launch failed")
        val waiting = CompletableFuture<Boolean>()
        every { usersPort.loadUsers() } returns listOf(user1, user2)
        every { runner.runAsync(user1, TaskMode.CHECK, any()) } returns CompletableFuture.failedFuture(fatal)
        every { runner.runAsync(user2, TaskMode.CHECK, any()) } returns waiting

        assertThatThrownBy { interactor.check(TargetCommand(TriggerType.MANUAL)) }.isSameAs(fatal)
        assertThat(waiting).isCancelled()
    }

    @Test
    fun `요청한 사용자 ID가 설정에 없으면 예외를 던진다`() {
        every { usersPort.loadUsers() } returns listOf(user1, user2)

        assertThatThrownBy { interactor.check(TargetCommand(TriggerType.MANUAL, listOf("missing"))) }
            .isInstanceOfSatisfying(InvalidManualUserSelectionException::class.java) {
                assertThat(it.invalidUserIds).containsExactly("missing")
                assertThat(it.availableUserIds).containsExactly("user1", "user2")
            }
    }

    private fun user(id: String) = LottoUser(id, "password", 1, "$id@nowstart.org", false)
}
