package org.nowstart.lotto.adapter.inbound.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import java.net.URI
import org.nowstart.lotto.adapter.inbound.api.response.ConflictResponse
import org.nowstart.lotto.adapter.inbound.api.response.ManualExecutionErrorResponse
import org.nowstart.lotto.adapter.inbound.api.response.StartResponse
import org.nowstart.lotto.application.exception.InvalidManualUserSelectionException
import org.nowstart.lotto.application.port.inbound.ExecutionView
import org.nowstart.lotto.application.port.inbound.JobUseCase
import org.nowstart.lotto.application.port.inbound.TargetCommand
import org.nowstart.lotto.domain.type.TaskMode
import org.nowstart.lotto.domain.type.TriggerType
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private val log = LoggerFactory.getLogger(ManualController::class.java)

@RestController
@RequestMapping("/api/lotto")
@Tag(name = "Lotto Manual", description = "로또 수동 실행 API")
class ManualController(private val jobUseCase: JobUseCase) {

    @Operation(
        summary = "로또 결과 확인",
        description = "결과 확인 작업을 시작하고 즉시 202를 돌려줍니다. 진행 상태는 Location의 실행 조회 API로 봅니다. " +
            "userId를 지정하면 해당 유저만, 없으면 전체 유저를 실행합니다.",
    )
    @PostMapping("/check")
    fun checkLottoResults(@RequestParam(name = "userId", required = false) userIds: List<String>?): ResponseEntity<Any> =
        start(TaskMode.CHECK, userIds)

    @Operation(
        summary = "로또 구매",
        description = "구매 작업을 시작하고 즉시 202를 돌려줍니다. 사이트 장애 시 자동으로 다시 시도하고, 판매 시간 밖이면 " +
            "다음 판매 시작에 실행합니다. 같은 계정 작업이 이미 진행 중이면 409와 기존 작업을 돌려줍니다.",
    )
    @PostMapping("/buy")
    fun buyLottoTickets(@RequestParam(name = "userId", required = false) userIds: List<String>?): ResponseEntity<Any> =
        start(TaskMode.PURCHASE, userIds)

    @Operation(summary = "실행 조회", description = "계정별 작업 상태(시도 횟수, 다음 시도 시각, 마지막 오류)를 봅니다.")
    @GetMapping("/executions/{executionId}")
    fun execution(@PathVariable executionId: String): ResponseEntity<ExecutionView> =
        jobUseCase.find(executionId)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

    @Operation(summary = "최근 실행 목록", description = "최근 24시간, 최대 50건의 실행을 최신순으로 봅니다.")
    @GetMapping("/executions")
    fun executions(): List<ExecutionView> = jobUseCase.recent()

    private fun start(mode: TaskMode, userIds: List<String>?): ResponseEntity<Any> {
        log.info("[Manual] {} request received userIds={}", mode, userIds)
        val result = jobUseCase.start(mode, TargetCommand(TriggerType.MANUAL, userIds))
        val execution = result.execution
            ?: return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ConflictResponse("같은 계정의 작업이 이미 진행 중입니다.", result.alreadyActive))
        return ResponseEntity.accepted()
            .location(URI.create("/api/lotto/executions/${execution.id}"))
            .body(StartResponse(execution, result.alreadyActive))
    }

    @ExceptionHandler(InvalidManualUserSelectionException::class)
    fun handleInvalidUserSelection(exception: InvalidManualUserSelectionException): ResponseEntity<ManualExecutionErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ManualExecutionErrorResponse("유효하지 않은 userId가 포함되어 있습니다.", exception.invalidUserIds, exception.availableUserIds),
        )
}
