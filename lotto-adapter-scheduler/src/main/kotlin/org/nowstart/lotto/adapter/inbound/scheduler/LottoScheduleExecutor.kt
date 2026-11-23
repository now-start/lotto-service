package org.nowstart.lotto.adapter.inbound.scheduler

import org.nowstart.lotto.application.port.inbound.LottoUseCase
import org.nowstart.lotto.application.port.inbound.TargetCommand
import org.nowstart.lotto.domain.type.TaskMode
import org.nowstart.lotto.domain.type.TriggerType
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val log = LoggerFactory.getLogger(LottoScheduleExecutor::class.java)

@Component
@Profile("!local")
class LottoScheduleExecutor(private val lottoUseCase: LottoUseCase) {

    /** 작업만 시작하고 기다리지 않는다(재시도·통지는 작업 단위로 처리). 시간대는 판매 달력과 같은 Asia/Seoul로 고정. */
    @Scheduled(cron = "\${lotto.cron.check}", zone = "Asia/Seoul")
    fun checkLottoResults() {
        log.info("[Schedule] Check Start")
        lottoUseCase.start(TaskMode.CHECK, TargetCommand.all(TriggerType.SCHEDULE))
    }

    @Scheduled(cron = "\${lotto.cron.buy}", zone = "Asia/Seoul")
    fun buyLottoTickets() {
        log.info("[Schedule] Purchase Start")
        lottoUseCase.start(TaskMode.PURCHASE, TargetCommand.all(TriggerType.SCHEDULE))
    }
}
