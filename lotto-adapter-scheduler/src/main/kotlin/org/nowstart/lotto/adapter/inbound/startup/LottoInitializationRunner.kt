package org.nowstart.lotto.adapter.inbound.startup

import org.nowstart.lotto.application.port.inbound.InitializeLottoUseCase
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class LottoInitializationRunner(private val initializeLottoUseCase: InitializeLottoUseCase) : CommandLineRunner {

    override fun run(vararg args: String) {
        initializeLottoUseCase.initialize()
    }
}
