package org.nowstart.lotto.adapter.in.startup;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.nowstart.lotto.application.dto.InitializeLottoCommand;
import org.nowstart.lotto.application.port.in.InitializeLottoUseCase;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LottoInitializationRunner implements CommandLineRunner {

    private final InitializeLottoUseCase initializeLottoUseCase;

    @Override
    public void run(String @NonNull ... args) {
        initializeLottoUseCase.initialize(new InitializeLottoCommand());
    }
}
