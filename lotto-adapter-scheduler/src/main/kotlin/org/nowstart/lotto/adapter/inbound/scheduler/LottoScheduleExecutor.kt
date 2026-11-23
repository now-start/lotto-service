package org.nowstart.lotto.adapter.inbound.scheduler

import org.nowstart.lotto.application.port.inbound.LottoUseCase
import org.nowstart.lotto.application.port.inbound.TargetCommand
import org.nowstart.lotto.domain.type.TriggerType
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val log = LoggerFactory.getLogger(LottoScheduleExecutor::class.java)

@Component
@Profile("!local")
class LottoScheduleExecutor(private val lottoUseCase: LottoUseCase) {

    @Scheduled(cron = "\${lotto.cron.check}", zone = "\${lotto.cron.zone:Asia/Seoul}")
    fun checkLottoResults() {
        log.info("[Schedule] Check Start")
        lottoUseCase.check(TargetCommand.all(TriggerType.SCHEDULE))
    }

    @Scheduled(cron = "\${lotto.cron.buy}", zone = "\${lotto.cron.zone:Asia/Seoul}")
    fun buyLottoTickets() {
        log.info("[Schedule] Purchase Start")
        lottoUseCase.purchase(TargetCommand.all(TriggerType.SCHEDULE))
    }
}
