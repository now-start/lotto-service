package org.nowstart.lotto;

import com.microsoft.playwright.Browser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.data.dto.PageDto;
import org.nowstart.lotto.data.dto.LottoUserDto;
import org.nowstart.lotto.data.dto.MessageDto;
import org.nowstart.lotto.service.GoogleNotifyService;
import org.nowstart.lotto.service.LottoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@EnableScheduling
@EnableDiscoveryClient
@SpringBootApplication
@RequiredArgsConstructor
public class LottoServiceApplication implements CommandLineRunner {

    @Value("${lotto.init}")
    private boolean lottoInit;
    private final Browser browser;
    private final GoogleNotifyService googleNotifyService;
    private final LottoService lottoService;

    public static void main(String[] args) {
        SpringApplication.run(LottoServiceApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        if(lottoInit) {
            try (PageDto pageDto = new PageDto(browser)) {
                log.info("[run][LottoInit]");
                LottoUserDto lottoUserDto = lottoService.loginLotto(pageDto);
                googleNotifyService.send(MessageDto.builder()
                        .subject("⏳Lotto Init Test⏳")
                        .text(lottoUserDto.toString())
                        .build());
            }
        }
    }
}
