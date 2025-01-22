package org.nowstart.lotto;

import lombok.RequiredArgsConstructor;
import org.nowstart.lotto.service.notify.GoogleNotifyService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableDiscoveryClient
@SpringBootApplication
@RequiredArgsConstructor
public class LottoServiceApplication implements CommandLineRunner {

	private final GoogleNotifyService googleNotifyService;

	public static void main(String[] args) {
		SpringApplication.run(LottoServiceApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		googleNotifyService.send("test");
	}
}
