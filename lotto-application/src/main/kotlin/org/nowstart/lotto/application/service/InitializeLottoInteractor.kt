package org.nowstart.lotto.application.service

import org.nowstart.lotto.application.port.inbound.InitializeLottoUseCase
import org.nowstart.lotto.application.port.outbound.LoadLottoUsersPort
import org.nowstart.lotto.application.port.outbound.LottoAutomationPort
import org.nowstart.lotto.application.port.outbound.SendNotificationPort
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

private val log = LoggerFactory.getLogger(InitializeLottoInteractor::class.java)

/** init=true 사용자에 대해 기동 시 로그인 테스트 후 확인 메일을 보낸다. */
@Service
class InitializeLottoInteractor(
    private val loadLottoUsersPort: LoadLottoUsersPort,
    private val lottoAutomationPort: LottoAutomationPort,
    private val sendNotificationPort: SendNotificationPort,
    private val lottoNotificationFactory: LottoNotificationFactory,
    private val accountLocks: AccountLocks,
) : InitializeLottoUseCase {

    /** 기동을 막지 않도록 별도 스레드에서 돈다(사이트가 느리면 로그인에 수 분이 걸린다). */
    @Async("lottoTaskExecutor")
    override fun initialize() {
        for (user in loadLottoUsersPort.loadUsers()) {
            if (!user.init) {
                log.info("[Init][{}] Skip", user.id)
                continue
            }
            if (!accountLocks.tryAcquire(user.id)) {
                // 같은 계정 작업이 브라우저를 쓰는 중이면 두 번째 로그인으로 그 세션을 끊지 않도록 건너뛴다.
                log.warn("[Init][{}] Skip - 같은 계정 작업이 실행 중", user.id)
                continue
            }
            log.info("[Init][{}] Start", user.id)
            try {
                lottoAutomationPort.openSession().use { session ->
                    try {
                        val accountSnapshot = lottoAutomationPort.login(session, user)
                        sendNotificationPort.send(lottoNotificationFactory.createInitializationMessage(user, accountSnapshot))
                        log.info("[Init][{}] Success deposit={}", user.id, accountSnapshot.deposit)
                    } catch (failure: Throwable) {
                        session.markFailed()
                        throw failure
                    }
                }
            } catch (exception: Exception) {
                log.error("[Init][{}] Failed", user.id, exception)
            } finally {
                accountLocks.release(user.id)
            }
            log.info("[Init][{}] Complete", user.id)
        }
    }
}
