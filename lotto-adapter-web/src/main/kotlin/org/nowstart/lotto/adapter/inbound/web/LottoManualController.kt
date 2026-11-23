package org.nowstart.lotto.adapter.inbound.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.nowstart.lotto.adapter.inbound.web.response.LottoExecutionResponse
import org.nowstart.lotto.adapter.inbound.web.response.ManualExecutionErrorResponse
import org.nowstart.lotto.application.exception.InvalidManualUserSelectionException
import org.nowstart.lotto.application.port.inbound.LottoUseCase
import org.nowstart.lotto.application.port.inbound.TargetCommand
import org.nowstart.lotto.domain.type.TriggerType
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private val log = LoggerFactory.getLogger(LottoManualController::class.java)

@RestController
@RequestMapping("/api/lotto")
@Tag(name = "Lotto Manual", description = "로또 수동 실행 API")
class LottoManualController(private val lottoUseCase: LottoUseCase) {

    @Operation(
        summary = "로또 결과 확인",
        description = "로또 결과를 수동으로 확인합니다. userId를 지정하면 해당 유저만 실행하고, 없으면 전체 유저를 실행합니다.",
    )
    @PostMapping("/check")
    fun checkLottoResults(@RequestParam(name = "userId", required = false) userIds: List<String>?): LottoExecutionResponse {
        log.info("[Manual] Check request received userIds={}", userIds)
        return LottoExecutionResponse.from(lottoUseCase.check(TargetCommand(TriggerType.MANUAL, userIds)))
    }

    @Operation(
        summary = "로또 구매",
        description = "로또를 수동으로 구매한 뒤 구매 내역을 확인하고 번호를 메일로 전달합니다. userId를 지정하면 해당 유저만 실행하고, 없으면 전체 유저를 실행합니다.",
    )
    @PostMapping("/buy")
    fun buyLottoTickets(@RequestParam(name = "userId", required = false) userIds: List<String>?): LottoExecutionResponse {
        log.info("[Manual] Buy request received userIds={}", userIds)
        return LottoExecutionResponse.from(lottoUseCase.purchase(TargetCommand(TriggerType.MANUAL, userIds)))
    }

    @ExceptionHandler(InvalidManualUserSelectionException::class)
    fun handleInvalidUserSelection(exception: InvalidManualUserSelectionException): ResponseEntity<ManualExecutionErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ManualExecutionErrorResponse("유효하지 않은 userId가 포함되어 있습니다.", exception.invalidUserIds, exception.availableUserIds),
        )
}
