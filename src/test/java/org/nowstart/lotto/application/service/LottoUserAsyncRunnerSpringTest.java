package org.nowstart.lotto.application.service;

import static org.assertj.core.api.BDDAssertions.then;

import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nowstart.lotto.application.port.out.LottoAutomationPort;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.CheckResult;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.LottoAccountSnapshot;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.LottoResult;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.PurchaseReceipt;
import org.nowstart.lotto.application.port.out.LottoAutomationSession;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort.LottoUser;
import org.nowstart.lotto.application.port.out.SendNotificationPort;
import org.nowstart.lotto.config.AsyncConfig;
import org.nowstart.lotto.domain.type.TaskMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = {
        AsyncConfig.class,
        LottoUserAsyncRunner.class,
        LottoNotificationFactory.class,
        LottoUserAsyncRunnerSpringTest.Config.class
})
@DisplayName("Spring Async 사용자별 로또 작업 실행")
class LottoUserAsyncRunnerSpringTest {

    @jakarta.annotation.Resource
    private LottoUserRunner lottoUserRunner;

    @jakarta.annotation.Resource
    private BlockingLottoAutomationPort blockingLottoAutomationPort;

    @Test
    @DisplayName("Spring 프록시를 통해 사용자 작업을 비동기로 실행한다")
    void shouldRunUserTaskThroughSpringAsyncProxy() throws Exception {
        // 준비: 로그인 단계에서 작업 스레드가 대기하도록 구성한다
        LottoUser user = new LottoUser("user1", "password", 1, "user1@nowstart.org", false);

        // 실행: 사용자 작업을 비동기로 요청한다
        CompletableFuture<Boolean> future = lottoUserRunner.runAsync(user, TaskMode.CHECK, new AtomicBoolean(false));

        // 검증: 호출 스레드는 즉시 future를 받고 실제 작업은 다른 스레드에서 진행 중이다
        then(blockingLottoAutomationPort.awaitLoginStarted()).isTrue();
        then(future.isDone()).isFalse();
        then(blockingLottoAutomationPort.loginThread()).isNotSameAs(Thread.currentThread());

        blockingLottoAutomationPort.releaseLogin();
        then(future.get(5, TimeUnit.SECONDS)).isTrue();
    }

    @Configuration
    @EnableAsync
    static class Config {

        @Bean
        BlockingLottoAutomationPort blockingLottoAutomationPort() {
            return new BlockingLottoAutomationPort();
        }

        @Bean
        LottoAutomationPort lottoAutomationPort(BlockingLottoAutomationPort blockingLottoAutomationPort) {
            return blockingLottoAutomationPort;
        }

        @Bean
        SendNotificationPort sendNotificationPort() {
            return message -> {
            };
        }
    }

    private static class BlockingLottoAutomationPort implements LottoAutomationPort {

        private final CountDownLatch loginStarted = new CountDownLatch(1);
        private final CountDownLatch releaseLogin = new CountDownLatch(1);
        private final AtomicReference<Thread> loginThread = new AtomicReference<>();

        @Override
        public LottoAutomationSession openSession() {
            return () -> {
            };
        }

        @Override
        public LottoAccountSnapshot login(LottoAutomationSession session, LottoUser user) {
            loginThread.set(Thread.currentThread());
            loginStarted.countDown();
            try {
                if (!releaseLogin.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("login was not released");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return new LottoAccountSnapshot("ok", "1000");
        }

        @Override
        public Optional<PurchaseReceipt> buy(LottoAutomationSession session, LottoUser user, BooleanSupplier abortRequested) {
            throw new UnsupportedOperationException("purchase is not used in this test");
        }

        @Override
        public List<CheckResult> check(LottoAutomationSession session) {
            return List.of(new CheckResult(
                    new LottoResult("2026-01-01", "1000", "로또", "1,2,3,4,5,6", "1", "당첨", "5000"),
                    new byte[] {1}
            ));
        }

        @Override
        public CheckResult check(LottoAutomationSession session, PurchaseReceipt purchaseReceipt) {
            throw new UnsupportedOperationException("purchase check is not used in this test");
        }

        boolean awaitLoginStarted() throws InterruptedException {
            return loginStarted.await(5, TimeUnit.SECONDS);
        }

        void releaseLogin() {
            releaseLogin.countDown();
        }

        Thread loginThread() {
            return loginThread.get();
        }
    }
}
