package org.nowstart.lotto.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/lotto")
@RequiredArgsConstructor
@Tag(name = "Lotto API", description = "로또 관리 API")
public class ApiController {

    private final Job job;
    private final JobOperator jobOperator;

    @Operation(summary = "로또 결과 확인 수동 실행")
    @GetMapping("/check")
    public void checkLottoResults() {
        log.info("[API] Check Start");
        runJob(false, "⚠️로또 확인 실패⚠️");
    }

    @Operation(summary = "로또 구매 수동 실행")
    @GetMapping("/buy")
    public void buyLottoTickets() {
        log.info("[API] Purchase Start");
        runJob(true, "⚠️로또 구매 실패⚠️");
    }

    private void runJob(boolean buyLotto, String failureSubject) {
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("buyLotto", String.valueOf(buyLotto))
                .addString("failureSubject", failureSubject)
                .addLocalDateTime("runDate", LocalDateTime.now())
                .toJobParameters();

        try {
            jobOperator.start(job, jobParameters);
        } catch (Exception e) {
            log.error("[API] Job Launch Failed", e);
        }
    }
}
