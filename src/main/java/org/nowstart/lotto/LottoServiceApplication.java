package org.nowstart.lotto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.lotto.data.dto.LottoUserDto;
import org.nowstart.lotto.data.dto.MessageDto;
import org.nowstart.lotto.service.GoogleNotifyService;
import org.nowstart.lotto.service.LottoService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@Slf4j
@EnableDiscoveryClient
@SpringBootApplication
@RequiredArgsConstructor
public class LottoServiceApplication implements CommandLineRunner {

	private final GoogleNotifyService googleNotifyService;
	private final LottoService lottoService;

	public static void main(String[] args) {
		SpringApplication.run(LottoServiceApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		log.info("[run][LottoInit]");
		LottoUserDto lottoUserDto = lottoService.loginLotto();
		googleNotifyService.send(MessageDto.builder()
			.subject("⏳Lotto Init Test⏳")
			.text(lottoUserDto.toString())
			.build());
	}
}
