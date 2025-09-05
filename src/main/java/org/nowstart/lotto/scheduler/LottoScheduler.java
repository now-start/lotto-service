package org.nowstart.lotto.scheduler;

import com.microsoft.playwright.Browser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.data.dto.LottoResultDto;
import org.nowstart.lotto.data.dto.LottoUserDto;
import org.nowstart.lotto.data.dto.MessageDto;
import org.nowstart.lotto.data.dto.PageDto;
import org.nowstart.lotto.service.GoogleNotifyService;
import org.nowstart.lotto.service.LottoService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@Component
@RestController
@RequestMapping("/api/lotto")
@RequiredArgsConstructor
public class LottoScheduler {

    private final Browser browser;
    private final LottoService lottoService;
    private final GoogleNotifyService googleNotifyService;

    @Scheduled(cron = "${lotto.cron.check}")
    @GetMapping("/check")
    public String checkLottoResults() {
        try {
            executeLottoTask("⚠️로또 확인 실패⚠️", false);
            return "로또 확인 작업이 수동으로 실행되었습니다.";
        } catch (Exception e) {
            log.error("로또 확인 실행 중 오류 발생", e);
            return "로또 확인 작업 실행 중 오류가 발생했습니다.";
        }
    }

    @Scheduled(cron = "${lotto.cron.buy}")
    @GetMapping("/buy")
    public String buyLottoTickets() {
        try {
            executeLottoTask("⚠️로또 구매 실패⚠️", true);
            return "로또 구매 작업이 수동으로 실행되었습니다.";
        } catch (Exception e) {
            log.error("로또 구매 실행 중 오류 발생", e);
            return "로또 구매 작업 실행 중 오류가 발생했습니다.";
        }
    }

    private void executeLottoTask(String failureSubject, boolean buyLotto) {
        try (PageDto pageDto = new PageDto(browser)) {
            log.info("로또 작업 실행 시작 - 구매 여부: {}", buyLotto);

            LottoUserDto lottoUserDto = performLoginWithRetry(pageDto);

            if (buyLotto) {
                performPurchaseWithRetry(pageDto);
            }

            processLottoResults(pageDto, lottoUserDto);

        } catch (Exception e) {
            handleTaskFailure(e, failureSubject);
        }
    }

    private LottoUserDto performLoginWithRetry(PageDto pageDto) {
        log.info("로그인 시도 중...");
        return lottoService.loginLotto(pageDto);
    }

    private void performPurchaseWithRetry(PageDto pageDto) {
        log.info("로또 구매 시도 중...");
        lottoService.buyLotto(pageDto);
        log.info("로또 구매 완료");
    }

    private void processLottoResults(PageDto pageDto, LottoUserDto lottoUserDto) {
        log.info("로또 결과 확인 중...");
        List<LottoResultDto> lottoResultDtoList = lottoService.checkLotto(pageDto);

        if (!lottoResultDtoList.isEmpty()) {
            LottoResultDto latestResult = lottoResultDtoList.get(0);
            sendSuccessNotification(pageDto, lottoUserDto, latestResult);
        } else {
            log.info("확인할 로또 결과가 없습니다.");
        }
    }

    private void sendSuccessNotification(PageDto pageDto, LottoUserDto lottoUserDto, LottoResultDto result) {
        try {
            googleNotifyService.send(MessageDto.builder()
                    .subject(result.toString())
                    .text(lottoUserDto.toString())
                    .lottoImage(lottoService.detailLotto(pageDto, result))
                    .build());
            log.info("성공 알림 전송 완료");
        } catch (Exception e) {
            log.error("성공 알림 전송 실패", e);
        }
    }

    private void handleTaskFailure(Exception e, String failureSubject) {
        log.error("로또 작업 실행 실패: {}", e.getMessage(), e);

        try {
            googleNotifyService.send(MessageDto.builder()
                    .subject(failureSubject)
                    .text(buildFailureMessage(e))
                    .build());
        } catch (Exception notificationError) {
            log.error("실패 알림 전송도 실패함", notificationError);
        }
    }

    private String buildFailureMessage(Exception e) {
        return String.format("""
                        작업 실행 중 오류가 발생했습니다.
                        
                        오류 유형: %s
                        오류 메시지: %s
                        
                        자세한 내용은 서버 로그를 확인해 주세요.""",
                e.getClass().getSimpleName(),
                e.getMessage());
    }
}