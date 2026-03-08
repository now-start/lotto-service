package org.nowstart.lotto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.nowstart.lotto.data.dto.LottoUserDto;
import org.nowstart.lotto.data.dto.MessageDto;
import org.nowstart.lotto.data.dto.PageDto;
import org.nowstart.lotto.data.properties.LottoProperties;
import org.nowstart.lotto.service.LottoLoginService;
import org.nowstart.lotto.service.NotifyService;
import org.nowstart.lotto.service.PageService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@EnableScheduling
@SpringBootApplication
@EnableResilientMethods
@ConfigurationPropertiesScan
@RequiredArgsConstructor
public class LottoServiceApplication implements CommandLineRunner {

    private final LottoProperties lottoProperties;
    private final PageService pageService;
    private final NotifyService notifyService;
    private final LottoLoginService lottoLoginService;

    static void main(String[] args) {
        SpringApplication.run(LottoServiceApplication.class, args);
    }

    @Override
    public void run(String @NonNull ... args) {
        for (LottoProperties.User user : lottoProperties.getUsers()) {
            if (Boolean.FALSE.equals(user.getInit())) {
                log.info("[Init][{}] - Skip", user.getId());
                continue;
            }

            log.info("[Init][{}] - Start", user.getId());

            try (PageDto pageDto = pageService.createManagedPage()) {
                LottoUserDto lottoUserDto = lottoLoginService.login(pageDto.page(), user);
                notifyService.send(MessageDto.builder()
                        .subject(String.format("⏳[%s] Lotto Init Test⏳", user.getId()))
                        .text(lottoUserDto.toString())
                        .to(user.getEmail())
                        .build());
                log.info("[Init][{}] - Success, deposit: {}", user.getId(), lottoUserDto.getDeposit());
            } catch (Exception e) {
                log.error("[Init][{}] - Failed", user.getId(), e);
            }

            log.info("[Init][{}] - Complete", user.getId());
        }
    }
}
