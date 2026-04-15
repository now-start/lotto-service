package org.nowstart.lotto.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.application.dto.InitializeLottoCommand;
import org.nowstart.lotto.application.port.in.InitializeLottoUseCase;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort;
import org.nowstart.lotto.application.port.out.LottoAutomationPort;
import org.nowstart.lotto.application.port.out.LottoAutomationSession;
import org.nowstart.lotto.application.port.out.SendNotificationPort;
import org.nowstart.lotto.domain.model.LottoAccountSnapshot;
import org.nowstart.lotto.domain.model.LottoUser;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class InitializeLottoInteractor implements InitializeLottoUseCase {

    private final LoadLottoUsersPort loadLottoUsersPort;
    private final LottoAutomationPort lottoAutomationPort;
    private final SendNotificationPort sendNotificationPort;
    private final LottoNotificationFactory lottoNotificationFactory;

    @Override
    public void initialize(InitializeLottoCommand command) {
        for (LottoUser user : loadLottoUsersPort.loadUsers()) {
            if (!user.init()) {
                log.info("[Init][{}] Skip", user.id());
                continue;
            }

            log.info("[Init][{}] Start", user.id());
            try (LottoAutomationSession session = lottoAutomationPort.openSession()) {
                LottoAccountSnapshot accountSnapshot = lottoAutomationPort.login(session, user);
                sendNotificationPort.send(lottoNotificationFactory.createInitializationMessage(user, accountSnapshot));
                log.info("[Init][{}] Success deposit={}", user.id(), accountSnapshot.deposit());
            } catch (Exception exception) {
                log.error("[Init][{}] Failed", user.id(), exception);
            }
            log.info("[Init][{}] Complete", user.id());
        }
    }
}
