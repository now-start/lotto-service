package org.nowstart.lotto.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import org.junit.jupiter.api.Test
import org.nowstart.lotto.application.exception.InvalidManualUserSelectionException
import org.nowstart.lotto.application.port.inbound.LottoExecution
import org.nowstart.lotto.application.port.inbound.LottoUseCase
import org.nowstart.lotto.domain.type.ExecutionStatus
import org.nowstart.lotto.domain.type.TaskMode
import org.nowstart.lotto.domain.type.TriggerType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class LottoManualControllerTest {

    private val useCase = mockk<LottoUseCase>()
    private val mockMvc = MockMvcBuilders.standaloneSetup(LottoManualController(useCase)).build()

    @Test
    fun `POST check 요청이면 전체 사용자 확인 작업을 실행한다`() {
        every { useCase.check(match { it.trigger == TriggerType.MANUAL && it.userIds.isEmpty() }) } returns
            execution(TaskMode.CHECK, ExecutionStatus.SUCCESS, total = 2, success = 2, failed = 0)

        mockMvc.perform(post("/api/lotto/check"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mode").value("CHECK"))
            .andExpect(jsonPath("$.trigger").value("MANUAL"))
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.totalUsers").value(2))
            .andExpect(jsonPath("$.failedUsers").value(0))
    }

    @Test
    fun `GET check 요청은 허용하지 않는다`() {
        mockMvc.perform(get("/api/lotto/check")).andExpect(status().isMethodNotAllowed)
    }

    @Test
    fun `POST buy 요청이면 전체 사용자 구매 작업을 실행한다`() {
        every { useCase.purchase(match { it.trigger == TriggerType.MANUAL && it.userIds.isEmpty() }) } returns
            execution(TaskMode.PURCHASE, ExecutionStatus.PARTIAL_FAILURE, total = 2, success = 1, failed = 1)

        mockMvc.perform(post("/api/lotto/buy"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mode").value("PURCHASE"))
            .andExpect(jsonPath("$.status").value("PARTIAL_FAILURE"))
            .andExpect(jsonPath("$.successUsers").value(1))
            .andExpect(jsonPath("$.failedUsers").value(1))
    }

    @Test
    fun `userId 파라미터가 있으면 특정 사용자만 확인한다`() {
        every { useCase.check(match { it.userIds == listOf("user1") }) } returns
            execution(TaskMode.CHECK, ExecutionStatus.SUCCESS, total = 1, success = 1, failed = 0)

        mockMvc.perform(post("/api/lotto/check").param("userId", "user1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalUsers").value(1))
            .andExpect(jsonPath("$.successUsers").value(1))
    }

    @Test
    fun `존재하지 않는 userId가 있으면 400 응답을 반환한다`() {
        every { useCase.purchase(any()) } throws InvalidManualUserSelectionException(listOf("missing-user"), listOf("user1", "user2"))

        mockMvc.perform(post("/api/lotto/buy").param("userId", "missing-user"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("유효하지 않은 userId가 포함되어 있습니다."))
            .andExpect(jsonPath("$.invalidUserIds[0]").value("missing-user"))
            .andExpect(jsonPath("$.availableUserIds[0]").value("user1"))
    }

    private fun execution(mode: TaskMode, status: ExecutionStatus, total: Int, success: Int, failed: Int) = LottoExecution(
        mode, TriggerType.MANUAL, status,
        Instant.parse("2026-02-23T00:00:00Z"), Instant.parse("2026-02-23T00:01:00Z"), 60_000,
        total, success, failed,
    )
}
