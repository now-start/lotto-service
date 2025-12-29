package org.nowstart.lotto.scheduler;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.service.LottoService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Component
@RestController
@RequestMapping("/api/lotto")
@RequiredArgsConstructor
@Tag(name = "Lotto Scheduler", description = "로또 스케줄러 API - 수동 실행 엔드포인트")
public class LottoScheduler {

    private final LottoService lottoService;

    @Operation(
            summary = "로또 결과 확인",
            description = "로또 결과를 확인합니다. 스케줄러에 의해 자동 실행되며, 수동으로도 호출 가능합니다."
    )
    @GetMapping("/check")
    @Scheduled(cron = "${lotto.cron.check}")
    public void checkLottoResults() {
        log.info("[Schedule] Check Start");
        lottoService.executeLottoTask("⚠️로또 확인 실패⚠️", false);
    }

    @Operation(
            summary = "로또 구매",
            description = "로또를 구매하고 결과를 확인합니다. 스케줄러에 의해 자동 실행되며, 수동으로도 호출 가능합니다."
    )
    @GetMapping("/buy")
    @Scheduled(cron = "${lotto.cron.buy}")
    public void buyLottoTickets() {
        log.info("[Schedule] Purchase Start");
        lottoService.executeLottoTask("⚠️로또 구매 실패⚠️", true);
    }
}