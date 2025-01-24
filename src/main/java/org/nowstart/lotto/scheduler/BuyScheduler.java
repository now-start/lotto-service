package org.nowstart.lotto.scheduler;

import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.data.dto.LottoResultDto;
import org.nowstart.lotto.data.dto.LottoUserDto;
import org.nowstart.lotto.data.dto.MessageDto;
import org.nowstart.lotto.data.type.MessageType;
import org.nowstart.lotto.service.GoogleNotifyService;
import org.nowstart.lotto.service.LottoService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BuyScheduler {

    private final LottoService lottoService;
    private final GoogleNotifyService googleNotifyService;

    @Scheduled(cron = "0 0 9 * * 0")
    public void buyScheduler() throws MessagingException {
        log.info("[buyScheduler][loginLotto]");
        LottoUserDto lottoUserDto = lottoService.loginLotto();

        log.info("[buyScheduler][checkLotto]");
        LottoResultDto lottoResultDtoList = lottoService.checkLotto().get(0);
        if (MessageType.WIN.getText().equals(lottoResultDtoList.getResult())
            || MessageType.NO_WIN.getText().equals(lottoResultDtoList.getResult())) {
            googleNotifyService.send(MessageDto.builder()
                .subject(lottoResultDtoList.toString())
                .text(lottoUserDto.toString())
                .lottoImage(lottoService.detailLotto(lottoResultDtoList))
                .build());
        }

        log.info("[buyScheduler][buyLotto]");
        lottoService.buyLotto();

        log.info("[buyScheduler][checkLotto]");
        lottoResultDtoList = lottoService.checkLotto().get(0);
        if (MessageType.WAITE.getText().equals(lottoResultDtoList.getResult())) {
            googleNotifyService.send(MessageDto.builder()
                .subject(lottoResultDtoList.toString())
                .text(lottoUserDto.toString())
                .lottoImage(lottoService.detailLotto(lottoResultDtoList))
                .build());
        }
    }
}
