package org.nowstart.lotto.config;

import lombok.RequiredArgsConstructor;
import org.nowstart.lotto.data.dto.TaskResult;
import org.nowstart.lotto.data.entity.UserEntity;
import org.nowstart.lotto.data.properties.AppProperties;
import org.nowstart.lotto.service.TaskProcessor;
import org.nowstart.lotto.service.TaskReader;
import org.nowstart.lotto.service.TaskSkipListener;
import org.nowstart.lotto.service.TaskWriter;
import org.nowstart.lotto.service.UserRetryPolicy;
import org.nowstart.lotto.service.UserSkipPolicy;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class BatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final TaskReader taskReader;
    private final TaskProcessor taskProcessor;
    private final TaskWriter taskWriter;
    private final TaskSkipListener taskSkipListener;
    private final AppProperties properties;

    @Bean
    public Job job(Step step) {
        return new JobBuilder("job", jobRepository)
                .start(step)
                .build();
    }

    @Bean
    public Step step() {
        return new StepBuilder("step", jobRepository)
                .<UserEntity, TaskResult>chunk(1, transactionManager)
                .reader(taskReader)
                .processor(taskProcessor)
                .writer(taskWriter)
                .faultTolerant()
                .retryPolicy(new UserRetryPolicy())
                .skipPolicy(new UserSkipPolicy())
                .listener(taskSkipListener)
                .build();
    }
}
