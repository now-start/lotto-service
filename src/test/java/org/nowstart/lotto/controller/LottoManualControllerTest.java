package org.nowstart.lotto.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nowstart.lotto.data.dto.BatchExecutionResult;
import org.nowstart.lotto.data.type.ExecutionStatus;
import org.nowstart.lotto.data.type.TaskMode;
import org.nowstart.lotto.data.type.TriggerType;
import org.nowstart.lotto.service.LottoService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class LottoManualControllerTest {

    @Mock
    private LottoService lottoService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new LottoManualController(lottoService)).build();
    }

    @Test
    void shouldExecuteCheckWithPost() throws Exception {
        BatchExecutionResult result = new BatchExecutionResult(
                TaskMode.CHECK_ONLY,
                TriggerType.MANUAL,
                ExecutionStatus.SUCCESS,
                Instant.parse("2026-02-23T00:00:00Z"),
                Instant.parse("2026-02-23T00:01:00Z"),
                60000,
                2,
                2,
                0
        );
        when(lottoService.execute(eq(TaskMode.CHECK_ONLY), eq(TriggerType.MANUAL))).thenReturn(result);

        mockMvc.perform(post("/api/lotto/check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("CHECK_ONLY"))
                .andExpect(jsonPath("$.trigger").value("MANUAL"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.totalUsers").value(2))
                .andExpect(jsonPath("$.failedUsers").value(0));
    }

    @Test
    void shouldRejectGetForCheck() throws Exception {
        mockMvc.perform(get("/api/lotto/check"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void shouldExecuteBuyWithPost() throws Exception {
        BatchExecutionResult result = new BatchExecutionResult(
                TaskMode.BUY_AND_CHECK,
                TriggerType.MANUAL,
                ExecutionStatus.PARTIAL_FAILURE,
                Instant.parse("2026-02-23T00:00:00Z"),
                Instant.parse("2026-02-23T00:02:00Z"),
                120000,
                2,
                1,
                1
        );
        when(lottoService.execute(eq(TaskMode.BUY_AND_CHECK), eq(TriggerType.MANUAL))).thenReturn(result);

        mockMvc.perform(post("/api/lotto/buy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("BUY_AND_CHECK"))
                .andExpect(jsonPath("$.status").value("PARTIAL_FAILURE"))
                .andExpect(jsonPath("$.successUsers").value(1))
                .andExpect(jsonPath("$.failedUsers").value(1));
    }
}
