package org.nowstart.lotto.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.data.dto.ManualExecutionResponse;
import org.nowstart.lotto.data.type.TaskMode;
import org.nowstart.lotto.data.type.TriggerType;
import org.nowstart.lotto.service.LottoService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
            description = "로또 결과를 수동으로 확인합니다. 즉시 실행 후 완료 결과를 반환합니다."
    )
    @PostMapping("/check")
    public ManualExecutionResponse checkLottoResults() {
        log.info("[Manual] Check request received");
        return ManualExecutionResponse.from(lottoService.execute(TaskMode.CHECK_ONLY, TriggerType.MANUAL));
    }

    @Operation(
            summary = "로또 구매",
            description = "로또를 수동으로 구매하고 결과를 확인합니다. 즉시 실행 후 완료 결과를 반환합니다."
    )
    @PostMapping("/buy")
    public ManualExecutionResponse buyLottoTickets() {
        log.info("[Manual] Buy request received");
        return ManualExecutionResponse.from(lottoService.execute(TaskMode.BUY_AND_CHECK, TriggerType.MANUAL));
    }
}
