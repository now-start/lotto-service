package org.nowstart.lotto.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import org.junit.jupiter.api.Test
import org.nowstart.lotto.application.exception.InvalidManualUserSelectionException
import org.nowstart.lotto.application.port.inbound.ExecutionView
import org.nowstart.lotto.application.port.inbound.JobState
import org.nowstart.lotto.application.port.inbound.LottoUseCase
import org.nowstart.lotto.application.port.inbound.StartResult
import org.nowstart.lotto.application.port.inbound.UserJobView
import org.nowstart.lotto.domain.type.TaskMode
import org.nowstart.lotto.domain.type.TriggerType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class LottoManualControllerTest {

    private val useCase = mockk<LottoUseCase>()
    private val mockMvc = MockMvcBuilders.standaloneSetup(LottoManualController(useCase)).build()

    @Test
    fun `POST buy는 작업을 시작하고 즉시 202와 실행 위치를 돌려준다`() {
        every { useCase.start(TaskMode.PURCHASE, match { it.trigger == TriggerType.MANUAL && it.userIds.isEmpty() }) } returns
            StartResult(execution("exec-1", TaskMode.PURCHASE, job("exec-1", "user1", JobState.QUEUED)), emptyList())

        mockMvc.perform(post("/api/lotto/buy"))
            .andExpect(status().isAccepted)
            .andExpect(header().string("Location", "/api/lotto/executions/exec-1"))
            .andExpect(jsonPath("$.execution.id").value("exec-1"))
            .andExpect(jsonPath("$.execution.jobs[0].userId").value("user1"))
            .andExpect(jsonPath("$.execution.jobs[0].state").value("QUEUED"))
    }

    @Test
    fun `요청한 계정이 모두 진행 중이면 409와 기존 작업을 돌려준다`() {
        every { useCase.start(TaskMode.PURCHASE, any()) } returns
            StartResult(null, listOf(job("exec-0", "user1", JobState.RETRY_SCHEDULED)))

        mockMvc.perform(post("/api/lotto/buy"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.alreadyActive[0].executionId").value("exec-0"))
            .andExpect(jsonPath("$.alreadyActive[0].state").value("RETRY_SCHEDULED"))
    }

    @Test
    fun `POST check는 지정한 사용자만 확인 작업을 시작한다`() {
        every { useCase.start(TaskMode.CHECK, match { it.userIds == listOf("user1") }) } returns
            StartResult(execution("exec-2", TaskMode.CHECK, job("exec-2", "user1", JobState.QUEUED)), emptyList())

        mockMvc.perform(post("/api/lotto/check").param("userId", "user1"))
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.execution.mode").value("CHECK"))
    }

    @Test
    fun `GET check 요청은 허용하지 않는다`() {
        mockMvc.perform(get("/api/lotto/check")).andExpect(status().isMethodNotAllowed)
    }

    @Test
    fun `실행 조회는 계정별 상태를 돌려주고 없으면 404다`() {
        every { useCase.find("exec-1") } returns execution("exec-1", TaskMode.PURCHASE, job("exec-1", "user1", JobState.SUCCEEDED))
        every { useCase.find("missing") } returns null
        every { useCase.recent() } returns emptyList()

        mockMvc.perform(get("/api/lotto/executions/exec-1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.jobs[0].state").value("SUCCEEDED"))
        mockMvc.perform(get("/api/lotto/executions/missing")).andExpect(status().isNotFound)
        mockMvc.perform(get("/api/lotto/executions")).andExpect(status().isOk)
    }

    @Test
    fun `존재하지 않는 userId가 있으면 400 응답을 반환한다`() {
        every { useCase.start(any(), any()) } throws InvalidManualUserSelectionException(listOf("missing-user"), listOf("user1", "user2"))

        mockMvc.perform(post("/api/lotto/buy").param("userId", "missing-user"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.invalidUserIds[0]").value("missing-user"))
            .andExpect(jsonPath("$.availableUserIds[0]").value("user1"))
    }

    private fun execution(id: String, mode: TaskMode, vararg jobs: UserJobView) =
        ExecutionView(id, mode, TriggerType.MANUAL, Instant.parse("2026-11-23T03:00:00Z"), jobs.toList())

    private fun job(executionId: String, userId: String, state: JobState) =
        UserJobView(executionId, userId, TaskMode.PURCHASE, state, 0, 1252, null, null)
}
