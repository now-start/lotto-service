package org.nowstart.lotto.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchQuartzJob extends QuartzJobBean {

    private final Job job;
    private final JobOperator jobOperator;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        boolean buyLotto = context.getMergedJobDataMap().getBoolean("buyLotto");
        String failureSubject = context.getMergedJobDataMap().getString("failureSubject");

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("buyLotto", String.valueOf(buyLotto))
                .addString("failureSubject", failureSubject)
                .addLocalDateTime("runDate", LocalDateTime.now())
                .toJobParameters();

        try {
            jobOperator.start(job, jobParameters);
        } catch (Exception e) {
            log.error("[Quartz] Job Launch Failed", e);
        }
    }
}
