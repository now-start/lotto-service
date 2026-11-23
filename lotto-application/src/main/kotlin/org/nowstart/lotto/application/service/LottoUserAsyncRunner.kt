package org.nowstart.lotto.application.service

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import org.nowstart.lotto.application.port.outbound.CheckResult
import org.nowstart.lotto.application.port.outbound.Delivery
import org.nowstart.lotto.application.port.outbound.LottoAccountSnapshot
import org.nowstart.lotto.application.port.outbound.LottoAutomationPort
import org.nowstart.lotto.application.port.outbound.LottoAutomationSession
import org.nowstart.lotto.application.port.outbound.LottoUser
import org.nowstart.lotto.application.port.outbound.NotificationMessage
import org.nowstart.lotto.application.port.outbound.SendNotificationPort
import org.nowstart.lotto.domain.exception.LottoAutomationException
import org.nowstart.lotto.domain.type.StepType
import org.nowstart.lotto.domain.type.TaskMode
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

private val log = LoggerFactory.getLogger(LottoUserAsyncRunner::class.java)

interface LottoUserRunner {

    /**
     * @param abortSignal 호출자 타임아웃 등으로 작업을 중단시키기 위한 신호. 러너는 세션 확보/로그인/구매 등
     *                    안전 지점에서 이 값을 확인해, 특히 실거래(구매) 직전에는 반드시 확인 후 중단한다.
     */
    fun runAsync(user: LottoUser, mode: TaskMode, abortSignal: AtomicBoolean): CompletableFuture<Boolean>
}

@Service
class LottoUserAsyncRunner(
    private val lottoAutomationPort: LottoAutomationPort,
    private val sendNotificationPort: SendNotificationPort,
    private val lottoNotificationFactory: LottoNotificationFactory,
    private val lottoPurchaseProcedure: LottoPurchaseProcedure,
) : LottoUserRunner {

    @Async("lottoTaskExecutor")
    override fun runAsync(user: LottoUser, mode: TaskMode, abortSignal: AtomicBoolean): CompletableFuture<Boolean> =
        CompletableFuture.completedFuture(runUser(user, mode, abortSignal))

    private fun runUser(user: LottoUser, mode: TaskMode, abortSignal: AtomicBoolean): Boolean {
        if (abortSignal.get()) {
            // 세션을 열기 전이라 브라우저 permit을 잡지 않은 상태 → 바로 통지해도 무방.
            return aborted(user, mode, "작업 시작 전")
        }
        if (mode == TaskMode.PURCHASE) {
            // 판매 시간 밖이거나 직전 구매 판정 대기 중이면 브라우저를 열지 않는다.
            lottoPurchaseProcedure.precheck(user)?.let { return report(user, null, it, null) }
        }

        // 브라우저 작업은 세션 안에서만 수행하고, 통지(SMTP)는 세션이 닫혀 permit이 해제된 뒤에 보낸다.
        // (성공/실패/중단 통지가 브라우저 슬롯을 붙잡아 다른 사용자가 세마포어에서 대기하는 것을 방지)
        var accountSnapshot: LottoAccountSnapshot? = null
        var results: List<CheckResult> = emptyList()
        var purchaseOutcome: PurchaseOutcome? = null
        var abortPhase: String? = null
        var checkAbortedAfterCompletion = false
        var openedSession: LottoAutomationSession? = null
        try {
            lottoAutomationPort.openSession().also { openedSession = it }.use { session ->
                try {
                    if (abortSignal.get()) {
                        session.markFailed()
                        abortPhase = "세션 확보 직후"
                    } else {
                        log.info("[Task][{}] Start mode={}", user.id, mode)
                        val account = runStep(StepType.LOGIN, user) { lottoAutomationPort.login(session, user) }
                        accountSnapshot = account
                        if (mode == TaskMode.PURCHASE) {
                            val outcome = runStep(StepType.PURCHASE, user) {
                                lottoPurchaseProcedure.purchase(session, user, account) { abortSignal.get() }
                            }
                            purchaseOutcome = outcome
                            if (outcome.needsDiagnostics || outcome is PurchaseOutcome.Aborted) {
                                session.markFailed()
                            }
                        } else {
                            results = runStep(StepType.CHECK, user) { lottoAutomationPort.check(session) }
                        }
                    }
                    if (abortPhase == null && mode == TaskMode.CHECK && abortSignal.get()) {
                        session.markFailed()
                        checkAbortedAfterCompletion = true
                    }
                } catch (failure: Throwable) {
                    session.markFailed()
                    throw failure
                }
            }
        } catch (exception: LottoAutomationException) {
            // 세션이 이미 닫혀(permit 해제) 아래 통지가 브라우저 슬롯을 점유하지 않는다.
            log.error("[Task][{}] Failed mode={} step={}", user.id, mode, exception.stepType, exception)
            sendNotification(user, withTrace(lottoNotificationFactory.createFailureMessage(user, mode, exception), openedSession), "failure")
            return false
        } catch (exception: Exception) {
            log.error("[Task][{}] Failed mode={} step=unknown", user.id, mode, exception)
            sendNotification(user, withTrace(lottoNotificationFactory.createFailureMessage(user, mode, exception), openedSession), "failure")
            return false
        }

        // 여기서부터 세션이 닫혀(permit 해제된) 상태 → 통지는 브라우저 슬롯을 붙잡지 않는다.
        abortPhase?.let { return aborted(user, mode, it, openedSession) }
        if (mode == TaskMode.PURCHASE) {
            val outcome = purchaseOutcome ?: return aborted(user, mode, "구매 결과 없음", openedSession)
            return report(user, accountSnapshot, outcome, openedSession)
        }
        if (checkAbortedAfterCompletion || abortSignal.get()) {
            // CHECK 진행 중 호출자 타임아웃 → 호출자는 이미 실패 보고. 뒤늦은 성공 통지로 상태 불일치를 만들지 않는다.
            log.warn("[Task][{}] Check completed but aborted meanwhile - suppressing success notification", user.id)
            return false
        }

        return try {
            val account = requireNotNull(accountSnapshot) { "login must have produced an account snapshot" }
            lottoNotificationFactory.createCheckSuccessMessage(user, account, results)
                ?.let { sendNotification(user, it, "success") }
            log.info("[Task][{}] Success mode={} deposit={}", user.id, mode, account.deposit)
            true
        } catch (exception: Exception) {
            // 성공 메시지 포맷팅(스크랩 데이터 파싱 등) 중 오류가 나도 사용자에게 실패 통지를 보낸다.
            log.error("[Task][{}] Failed mode={} step=notify", user.id, mode, exception)
            sendNotification(user, lottoNotificationFactory.createFailureMessage(user, mode, exception), "failure")
            false
        }
    }

    /** 구매 결과를 통지하고 성공 여부를 돌려준다. 통지 실패는 구매 결과를 바꾸지 않는다. */
    private fun report(
        user: LottoUser,
        accountSnapshot: LottoAccountSnapshot?,
        outcome: PurchaseOutcome,
        session: LottoAutomationSession?,
    ): Boolean {
        if (outcome is PurchaseOutcome.Aborted) {
            return aborted(user, TaskMode.PURCHASE, outcome.phase, session)
        }
        log.info("[Task][{}] Purchase outcome={}", user.id, outcome.javaClass.simpleName)
        try {
            lottoNotificationFactory.createPurchaseOutcomeMessage(user, accountSnapshot, outcome)
                ?.let { if (outcome.needsDiagnostics) withTrace(it, session) else it }
                ?.let { sendNotification(user, it, outcome.javaClass.simpleName) }
        } catch (exception: RuntimeException) {
            log.error("[Task][{}] Purchase notification build failed outcome={}", user.id, outcome, exception)
        }
        return outcome.isSuccess
    }

    /**
     * 실제 작업 수행 전에 중단된 경우의 처리. PURCHASE는 스케줄 구매가 조용히 누락되지 않도록 실패 통지를 보낸다.
     * CHECK는 조회 미수행이 치명적이지 않으므로 통지하지 않는다(호출자가 이미 타임아웃 실패로 보고).
     */
    private fun aborted(user: LottoUser, mode: TaskMode, phase: String, session: LottoAutomationSession? = null): Boolean {
        log.warn("[Task][{}] Aborted ({}) - 호출자 타임아웃 mode={}", user.id, phase, mode)
        if (mode == TaskMode.PURCHASE) {
            val abortException = LottoAutomationException(
                StepType.PURCHASE,
                user.id,
                IllegalStateException("구매가 최종 확정 전에 중단/미제출되었습니다 ($phase)"),
            )
            sendNotification(user, withTrace(lottoNotificationFactory.createFailureMessage(user, mode, abortException), session), "failure")
        }
        return false
    }

    private fun withTrace(message: NotificationMessage, session: LottoAutomationSession?): NotificationMessage {
        val trace = session?.failureTrace() ?: return message
        return message.withAttachment(trace)
    }

    private fun sendNotification(user: LottoUser, message: NotificationMessage, kind: String) {
        try {
            if (sendNotificationPort.send(message) == Delivery.QUEUED) {
                log.warn("[Notify][{}] {} notification queued for resend", user.id, kind)
            } else {
                log.info("[Notify][{}] {} notification sent", user.id, kind)
            }
        } catch (notificationError: Exception) {
            log.error("[Notify][{}] {} notification failed", user.id, kind, notificationError)
        }
    }

    private fun <T> runStep(stepType: StepType, user: LottoUser, action: () -> T): T = try {
        action()
    } catch (exception: LottoAutomationException) {
        throw exception
    } catch (exception: Exception) {
        throw LottoAutomationException(stepType, user.id, exception)
    }
}
