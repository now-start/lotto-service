package org.nowstart.lotto.adapter.inbound.startup

import org.nowstart.lotto.application.port.inbound.InitializeUseCase
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class InitializationRunner(private val initializeUseCase: InitializeUseCase) : CommandLineRunner {

    override fun run(vararg args: String) {
        initializeUseCase.initialize()
    }
}
