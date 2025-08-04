package org.nowstart.lotto.scheduler;

import com.microsoft.playwright.Browser;
import jakarta.mail.MessagingException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.data.dto.LottoResultDto;
import org.nowstart.lotto.data.dto.LottoUserDto;
import org.nowstart.lotto.data.dto.MessageDto;
import org.nowstart.lotto.data.dto.PageDto;
import org.nowstart.lotto.data.type.MessageType;
import org.nowstart.lotto.service.GoogleNotifyService;
import org.nowstart.lotto.service.LottoService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Component
@RestController
@RequiredArgsConstructor
public class Scheduler {

    private final Browser browser;
    private final LottoService lottoService;
    private final GoogleNotifyService googleNotifyService;

    @GetMapping("/check")
    @Scheduled(cron = "${lotto.cron.check}")
    public void checkScheduler() throws MessagingException, UnsupportedEncodingException {
        executeLottoTask("⚠️로또 확인 실패⚠️", false);
    }

    @GetMapping("/buy")
    @Scheduled(cron = "${lotto.cron.buy}")
    public void buyScheduler() throws MessagingException, UnsupportedEncodingException {
        executeLottoTask("⚠️로또 구매 실패⚠️", true);
    }

    private void executeLottoTask(String failureSubject, boolean buyLotto) throws MessagingException, UnsupportedEncodingException {
        try (PageDto pageDto = new PageDto(browser)) {
            log.info("[executeLottoTask][loginLotto]");
            LottoUserDto lottoUserDto = lottoService.loginLotto(pageDto);

            if (buyLotto) {
                log.info("[executeLottoTask][buyLotto]");
                lottoService.buyLotto(pageDto);
            }

            log.info("[executeLottoTask][checkLotto]");
            List<LottoResultDto> lottoResultDtoList = lottoService.checkLotto(pageDto);
            if (!lottoResultDtoList.isEmpty()) {
                LottoResultDto lottoResultDto = lottoResultDtoList.get(0);
                if (buyLotto == MessageType.WAITE.getText().equals(lottoResultDto.getResult())) {
                    googleNotifyService.send(MessageDto.builder()
                        .subject(lottoResultDto.toString())
                        .text(lottoUserDto.toString())
                        .lottoImage(lottoService.detailLotto(pageDto, lottoResultDto))
                        .build());
                } else {
                    throw new IllegalArgumentException();
                }
            }
        } catch (Exception e) {
            log.error("[executeLottoTask][Exception]", e);
            googleNotifyService.send(MessageDto.builder()
                .subject(failureSubject)
                .text("자세한 내용은 로그를 확인해 주세요.")
                .build());
        }
    }
}