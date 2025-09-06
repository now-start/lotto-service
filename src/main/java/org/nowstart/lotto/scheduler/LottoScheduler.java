package org.nowstart.lotto.scheduler;

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
public class LottoScheduler {

    private final LottoService lottoService;

    @Scheduled(cron = "${lotto.cron.check}")
    @GetMapping("/check")
    public void checkLottoResults() {
        log.info("로또 확인 스케줄 실행");
        lottoService.executeLottoTask("⚠️로또 확인 실패⚠️", false);
    }

    @Scheduled(cron = "${lotto.cron.buy}")
    @GetMapping("/buy")
    public void buyLottoTickets() {
        log.info("로또 구매 스케줄 실행");
        lottoService.executeLottoTask("⚠️로또 구매 실패⚠️", true);
    }
}