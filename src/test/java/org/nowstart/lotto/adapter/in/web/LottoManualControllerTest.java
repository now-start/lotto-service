package org.nowstart.lotto.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nowstart.lotto.application.port.in.LottoUseCase;
import org.nowstart.lotto.application.port.in.LottoUseCase.TargetCommand;
import org.nowstart.lotto.domain.exception.InvalidManualUserSelectionException;
import org.nowstart.lotto.domain.model.LottoExecution;
import org.nowstart.lotto.domain.type.ExecutionStatus;
import org.nowstart.lotto.domain.type.TaskMode;
import org.nowstart.lotto.domain.type.TriggerType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class LottoManualControllerTest {

    @Mock
    private LottoUseCase lottoUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new LottoManualController(lottoUseCase)).build();
    }

    @Test
    void shouldExecuteCheckWithPost() throws Exception {
        LottoExecution execution = new LottoExecution(
                TaskMode.CHECK,
                TriggerType.MANUAL,
                ExecutionStatus.SUCCESS,
                Instant.parse("2026-02-23T00:00:00Z"),
                Instant.parse("2026-02-23T00:01:00Z"),
                60000,
                2,
                2,
                0
        );
        when(lottoUseCase.check(argThat(command ->
                command.trigger() == TriggerType.MANUAL
                        && command.userIds().isEmpty()
        ))).thenReturn(execution);

        mockMvc.perform(post("/api/lotto/check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("CHECK"))
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
        LottoExecution execution = new LottoExecution(
                TaskMode.PURCHASE,
                TriggerType.MANUAL,
                ExecutionStatus.PARTIAL_FAILURE,
                Instant.parse("2026-02-23T00:00:00Z"),
                Instant.parse("2026-02-23T00:02:00Z"),
                120000,
                2,
                1,
                1
        );
        when(lottoUseCase.purchase(argThat(command ->
                command.trigger() == TriggerType.MANUAL
                        && command.userIds().isEmpty()
        ))).thenReturn(execution);

        mockMvc.perform(post("/api/lotto/buy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("PURCHASE"))
                .andExpect(jsonPath("$.status").value("PARTIAL_FAILURE"))
                .andExpect(jsonPath("$.successUsers").value(1))
                .andExpect(jsonPath("$.failedUsers").value(1));
    }

    @Test
    void shouldExecuteCheckForSpecificUser() throws Exception {
        LottoExecution execution = new LottoExecution(
                TaskMode.CHECK,
                TriggerType.MANUAL,
                ExecutionStatus.SUCCESS,
                Instant.parse("2026-02-23T00:00:00Z"),
                Instant.parse("2026-02-23T00:00:20Z"),
                20000,
                1,
                1,
                0
        );
        when(lottoUseCase.check(argThat(command ->
                command.trigger() == TriggerType.MANUAL
                        && List.of("user1").equals(command.userIds())
        ))).thenReturn(execution);

        mockMvc.perform(post("/api/lotto/check").param("userId", "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("CHECK"))
                .andExpect(jsonPath("$.totalUsers").value(1))
                .andExpect(jsonPath("$.successUsers").value(1));
    }

    @Test
    void shouldReturnBadRequestForInvalidUserSelection() throws Exception {
        when(lottoUseCase.purchase(any(TargetCommand.class)))
                .thenThrow(new InvalidManualUserSelectionException(List.of("missing-user"), List.of("user1", "user2")));

        mockMvc.perform(post("/api/lotto/buy").param("userId", "missing-user"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("유효하지 않은 userId가 포함되어 있습니다."))
                .andExpect(jsonPath("$.invalidUserIds[0]").value("missing-user"))
                .andExpect(jsonPath("$.availableUserIds[0]").value("user1"));
    }
}
