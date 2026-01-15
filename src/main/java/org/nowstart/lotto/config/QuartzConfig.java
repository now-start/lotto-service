package org.nowstart.lotto.config;

import org.nowstart.lotto.service.BatchQuartzJob;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuartzConfig {

    @Value("${lotto.cron.check}")
    private String checkCron;

    @Value("${lotto.cron.buy}")
    private String buyCron;

    @Bean
    public JobDetail checkJob() {
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put("buyLotto", false);
        jobDataMap.put("failureSubject", "⚠️로또 확인 실패⚠️");

        return JobBuilder.newJob(BatchQuartzJob.class)
                .withIdentity("checkJob")
                .usingJobData(jobDataMap)
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger checkTrigger(JobDetail checkJob) {
        return TriggerBuilder.newTrigger()
                .forJob(checkJob)
                .withIdentity("checkTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule(checkCron))
                .build();
    }

    @Bean
    public JobDetail buyJob() {
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put("buyLotto", true);
        jobDataMap.put("failureSubject", "⚠️로또 구매 실패⚠️");

        return JobBuilder.newJob(BatchQuartzJob.class)
                .withIdentity("buyJob")
                .usingJobData(jobDataMap)
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger buyTrigger(JobDetail buyJob) {
        return TriggerBuilder.newTrigger()
                .forJob(buyJob)
                .withIdentity("buyTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule(buyCron))
                .build();
    }
}
