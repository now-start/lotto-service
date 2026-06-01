package org.nowstart.lotto.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nowstart.lotto.application.port.in.LottoUseCase;
import org.nowstart.lotto.application.port.in.LottoUseCase.TargetCommand;
import org.nowstart.lotto.domain.exception.InvalidManualUserSelectionException;
import org.nowstart.lotto.application.port.in.LottoUseCase.LottoExecution;
import org.nowstart.lotto.domain.type.ExecutionStatus;
import org.nowstart.lotto.domain.type.TaskMode;
import org.nowstart.lotto.domain.type.TriggerType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
@DisplayName("로또 수동 실행 API")
class LottoManualControllerTest {

    @Mock
    private LottoUseCase lottoUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new LottoManualController(lottoUseCase)).build();
    }

    @Test
    @DisplayName("POST /check 요청이면 전체 사용자 확인 작업을 실행한다")
    void shouldExecuteCheckWithPost() throws Exception {
        // 준비: 전체 사용자 확인 작업이 성공 결과를 반환한다
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
        given(lottoUseCase.check(argThat(command ->
                command.trigger() == TriggerType.MANUAL
                        && command.userIds().isEmpty()
        ))).willReturn(execution);

        // 실행 및 검증: POST /check 응답에 실행 결과가 담긴다
        mockMvc.perform(post("/api/lotto/check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("CHECK"))
                .andExpect(jsonPath("$.trigger").value("MANUAL"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.totalUsers").value(2))
                .andExpect(jsonPath("$.failedUsers").value(0));
    }

    @Test
    @DisplayName("GET /check 요청이면 허용하지 않는다")
    void shouldRejectGetForCheck() throws Exception {
        // 실행 및 검증: 확인 API는 POST만 허용한다
        mockMvc.perform(get("/api/lotto/check"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("POST /buy 요청이면 전체 사용자 구매 작업을 실행한다")
    void shouldExecuteBuyWithPost() throws Exception {
        // 준비: 전체 사용자 구매 작업이 부분 실패 결과를 반환한다
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
        given(lottoUseCase.purchase(argThat(command ->
                command.trigger() == TriggerType.MANUAL
                        && command.userIds().isEmpty()
        ))).willReturn(execution);

        // 실행 및 검증: POST /buy 응답에 실행 결과가 담긴다
        mockMvc.perform(post("/api/lotto/buy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("PURCHASE"))
                .andExpect(jsonPath("$.status").value("PARTIAL_FAILURE"))
                .andExpect(jsonPath("$.successUsers").value(1))
                .andExpect(jsonPath("$.failedUsers").value(1));
    }

    @Test
    @DisplayName("userId 파라미터가 있으면 특정 사용자만 확인한다")
    void shouldExecuteCheckForSpecificUser() throws Exception {
        // 준비: user1 확인 작업이 성공 결과를 반환한다
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
        given(lottoUseCase.check(argThat(command ->
                command.trigger() == TriggerType.MANUAL
                        && List.of("user1").equals(command.userIds())
        ))).willReturn(execution);

        // 실행 및 검증: 응답에 단일 사용자 실행 결과가 담긴다
        mockMvc.perform(post("/api/lotto/check").param("userId", "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("CHECK"))
                .andExpect(jsonPath("$.totalUsers").value(1))
                .andExpect(jsonPath("$.successUsers").value(1));
    }

    @Test
    @DisplayName("존재하지 않는 userId가 있으면 400 응답을 반환한다")
    void shouldReturnBadRequestForInvalidUserSelection() throws Exception {
        // 준비: 유효하지 않은 사용자 선택 예외가 발생한다
        given(lottoUseCase.purchase(any(TargetCommand.class)))
                .willThrow(new InvalidManualUserSelectionException(List.of("missing-user"), List.of("user1", "user2")));

        // 실행 및 검증: 오류 응답에 잘못된 사용자와 가능한 사용자 목록이 담긴다
        mockMvc.perform(post("/api/lotto/buy").param("userId", "missing-user"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("유효하지 않은 userId가 포함되어 있습니다."))
                .andExpect(jsonPath("$.invalidUserIds[0]").value("missing-user"))
                .andExpect(jsonPath("$.availableUserIds[0]").value("user1"));
    }
}
