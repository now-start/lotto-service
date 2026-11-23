package org.nowstart.lotto.application.service

import java.util.concurrent.Executor
import org.nowstart.lotto.application.port.inbound.InitializeUseCase
import org.nowstart.lotto.application.port.outbound.AutomationPort
import org.nowstart.lotto.application.port.outbound.LoadUsersPort
import org.nowstart.lotto.application.port.outbound.SendNotificationPort
import org.nowstart.lotto.application.port.outbound.UserAccount
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

private val log = LoggerFactory.getLogger(InitializeInteractor::class.java)

/** init=true 사용자에 대해 기동 시 로그인 테스트 후 확인 메일을 보낸다. */
@Service
class InitializeInteractor(
    private val loadUsersPort: LoadUsersPort,
    private val automationPort: AutomationPort,
    private val sendNotificationPort: SendNotificationPort,
    private val notificationFactory: NotificationFactory,
    private val accountLocks: AccountLocks,
    @Qualifier("jobExecutor") private val executor: Executor,
) : InitializeUseCase {

    override fun initialize() {
        for (user in loadUsersPort.loadUsers()) {
            if (user.init) {
                executor.execute { initializeAccount(user) }
            } else {
                log.info("[Init][{}] Skip", user.id)
            }
        }
    }

    private fun initializeAccount(user: UserAccount) {
        if (!accountLocks.tryAcquire(user.id)) {
            log.warn("[Init][{}] Skip - 같은 계정 작업이 실행 중", user.id)
            return
        }
        log.info("[Init][{}] Start", user.id)
        try {
            automationPort.openSession().use { session ->
                try {
                    val accountSnapshot = automationPort.login(session, user)
                    sendNotificationPort.send(notificationFactory.createInitializationMessage(user, accountSnapshot))
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
