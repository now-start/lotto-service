package org.nowstart.lotto.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.data.dto.ManualExecutionErrorResponse;
import org.nowstart.lotto.data.dto.ManualExecutionResponse;
import org.nowstart.lotto.data.exception.InvalidManualUserSelectionException;
import org.nowstart.lotto.data.type.TaskMode;
import org.nowstart.lotto.data.type.TriggerType;
import org.nowstart.lotto.service.LottoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/lotto")
@RequiredArgsConstructor
@Tag(name = "Lotto Manual", description = "로또 수동 실행 API")
public class LottoManualController {

    private final LottoService lottoService;

    @Operation(
            summary = "로또 결과 확인",
            description = "로또 결과를 수동으로 확인합니다. userId를 지정하면 해당 유저만 실행하고, 없으면 전체 유저를 실행합니다."
    )
    @PostMapping("/check")
    public ManualExecutionResponse checkLottoResults(
            @RequestParam(name = "userId", required = false) List<String> userIds
    ) {
        log.info("[Manual] Check request received userIds={}", userIds);
        return ManualExecutionResponse.from(lottoService.execute(TaskMode.CHECK_ONLY, TriggerType.MANUAL, userIds));
    }

    @Operation(
            summary = "로또 구매",
            description = "로또를 수동으로 구매하고 결과를 확인합니다. userId를 지정하면 해당 유저만 실행하고, 없으면 전체 유저를 실행합니다."
    )
    @PostMapping("/buy")
    public ManualExecutionResponse buyLottoTickets(
            @RequestParam(name = "userId", required = false) List<String> userIds
    ) {
        log.info("[Manual] Buy request received userIds={}", userIds);
        return ManualExecutionResponse.from(lottoService.execute(TaskMode.BUY_AND_CHECK, TriggerType.MANUAL, userIds));
    }

    @ExceptionHandler(InvalidManualUserSelectionException.class)
    public ResponseEntity<ManualExecutionErrorResponse> handleInvalidUserSelection(
            InvalidManualUserSelectionException exception
    ) {
        ManualExecutionErrorResponse response = new ManualExecutionErrorResponse(
                "유효하지 않은 userId가 포함되어 있습니다.",
                exception.getInvalidUserIds(),
                exception.getAvailableUserIds()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}
