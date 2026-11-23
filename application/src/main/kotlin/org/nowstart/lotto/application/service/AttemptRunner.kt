package org.nowstart.lotto.application.service

import java.util.concurrent.atomic.AtomicBoolean
import org.nowstart.lotto.application.port.outbound.Attachment
import org.nowstart.lotto.application.port.outbound.CheckResult
import org.nowstart.lotto.application.port.outbound.AccountSnapshot
import org.nowstart.lotto.application.port.outbound.AutomationPort
import org.nowstart.lotto.application.port.outbound.AutomationSession
import org.nowstart.lotto.application.port.outbound.UserAccount
import org.nowstart.lotto.domain.exception.AutomationException
import org.nowstart.lotto.domain.type.StepType
import org.nowstart.lotto.domain.type.TaskMode
import org.springframework.stereotype.Service

/** 한 번의 시도 결과. 통지와 재시도 판단은 [JobService]가 한다. */
sealed interface AttemptResult {

    /** 구매 시도 결과(브라우저를 열기 전에 끝났으면 account가 없다). */
    data class Purchase(val outcome: PurchaseOutcome, val account: AccountSnapshot?, val trace: Attachment?) : AttemptResult

    data class Checked(val results: List<CheckResult>, val account: AccountSnapshot) : AttemptResult

    /** 예외로 끝남. 구매의 경우 확정 클릭 이후 예외는 구매 절차가 Uncertain으로 바꾸므로 여기에는 클릭 전 실패만 온다. */
    data class Failed(val error: Exception, val step: StepType?, val trace: Attachment?) : AttemptResult
}

/**
 * 브라우저 세션 하나로 한 번의 시도를 수행한다(로그인 → 구매 또는 결과 확인).
 * 세션을 닫고 시도 결과를 반환하면 [JobService]가 통지를 처리한다.
 */
@Service
class AttemptRunner(
    private val automationPort: AutomationPort,
    private val purchaseProcedure: PurchaseProcedure,
) {

    fun run(user: UserAccount, mode: TaskMode, abortSignal: AtomicBoolean, targetRound: Int?): AttemptResult {
        val purchaseRound = if (mode == TaskMode.PURCHASE) requireNotNull(targetRound) else null
        if (purchaseRound != null) {
            // 판매 시간 밖이거나 직전 구매 판정 대기 중이면 브라우저를 열지 않는다.
            purchaseProcedure.precheck(user, purchaseRound)?.let { return AttemptResult.Purchase(it, null, null) }
        }
        var openedSession: AutomationSession? = null
        val result = try {
            automationPort.openSession().also { openedSession = it }.use { session ->
                try {
                    val account = step(StepType.LOGIN, user) { automationPort.login(session, user) }
                    if (purchaseRound != null) {
                        val outcome = step(StepType.PURCHASE, user) {
                            purchaseProcedure.purchase(session, user, account, purchaseRound) { abortSignal.get() }
                        }
                        if (outcome.needsDiagnostics) {
                            session.markFailed()
                        }
                        AttemptResult.Purchase(outcome, account, null)
                    } else {
                        AttemptResult.Checked(step(StepType.CHECK, user) { automationPort.check(session) }, account)
                    }
                } catch (failure: Throwable) {
                    session.markFailed()
                    throw failure
                }
            }
        } catch (exception: AutomationException) {
            return AttemptResult.Failed(exception, exception.stepType, openedSession?.failureTrace())
        } catch (exception: Exception) {
            return AttemptResult.Failed(exception, null, openedSession?.failureTrace())
        }
        // 진단 자료(트레이스)는 세션을 닫을 때 만들어지므로 닫은 뒤에 붙인다.
        return if (result is AttemptResult.Purchase) result.copy(trace = openedSession?.failureTrace()) else result
    }

    private fun <T> step(stepType: StepType, user: UserAccount, action: () -> T): T = try {
        action()
    } catch (exception: AutomationException) {
        throw exception
    } catch (exception: Exception) {
        throw AutomationException(stepType, user.id, exception)
    }
}
