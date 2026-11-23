package org.nowstart.lotto.application.service

import java.util.concurrent.atomic.AtomicBoolean
import org.nowstart.lotto.application.port.outbound.Attachment
import org.nowstart.lotto.application.port.outbound.CheckResult
import org.nowstart.lotto.application.port.outbound.LottoAccountSnapshot
import org.nowstart.lotto.application.port.outbound.LottoAutomationPort
import org.nowstart.lotto.application.port.outbound.LottoAutomationSession
import org.nowstart.lotto.application.port.outbound.LottoUser
import org.nowstart.lotto.domain.exception.LottoAutomationException
import org.nowstart.lotto.domain.type.StepType
import org.nowstart.lotto.domain.type.TaskMode
import org.springframework.stereotype.Service

/** 한 번의 시도 결과. 통지와 재시도 판단은 [LottoJobService]가 한다. */
sealed interface AttemptResult {

    /** 구매 시도 결과(브라우저를 열기 전에 끝났으면 account가 없다). */
    data class Purchase(val outcome: PurchaseOutcome, val account: LottoAccountSnapshot?, val trace: Attachment?) : AttemptResult

    data class Checked(val results: List<CheckResult>, val account: LottoAccountSnapshot) : AttemptResult

    /** 예외로 끝남. 구매의 경우 확정 클릭 이후 예외는 구매 절차가 Uncertain으로 바꾸므로 여기에는 클릭 전 실패만 온다. */
    data class Failed(val error: Exception, val step: StepType?, val trace: Attachment?) : AttemptResult
}

/**
 * 브라우저 세션 하나로 한 번의 시도를 수행한다(로그인 → 구매 또는 결과 확인).
 * 세션은 이 안에서 열고 닫으며, 통지는 하지 않는다(세션이 닫혀 브라우저 슬롯이 풀린 뒤 오케스트레이터가 통지).
 */
@Service
class LottoAttemptRunner(
    private val lottoAutomationPort: LottoAutomationPort,
    private val lottoPurchaseProcedure: LottoPurchaseProcedure,
) {

    fun run(user: LottoUser, mode: TaskMode, abortSignal: AtomicBoolean): AttemptResult {
        if (mode == TaskMode.PURCHASE) {
            // 판매 시간 밖이거나 직전 구매 판정 대기 중이면 브라우저를 열지 않는다.
            lottoPurchaseProcedure.precheck(user)?.let { return AttemptResult.Purchase(it, null, null) }
        }
        var openedSession: LottoAutomationSession? = null
        val result = try {
            lottoAutomationPort.openSession().also { openedSession = it }.use { session ->
                try {
                    val account = step(StepType.LOGIN, user) { lottoAutomationPort.login(session, user) }
                    if (mode == TaskMode.PURCHASE) {
                        val outcome = step(StepType.PURCHASE, user) {
                            lottoPurchaseProcedure.purchase(session, user, account) { abortSignal.get() }
                        }
                        if (outcome.needsDiagnostics) {
                            session.markFailed()
                        }
                        AttemptResult.Purchase(outcome, account, null)
                    } else {
                        AttemptResult.Checked(step(StepType.CHECK, user) { lottoAutomationPort.check(session) }, account)
                    }
                } catch (failure: Throwable) {
                    session.markFailed()
                    throw failure
                }
            }
        } catch (exception: LottoAutomationException) {
            return AttemptResult.Failed(exception, exception.stepType, openedSession?.failureTrace())
        } catch (exception: Exception) {
            return AttemptResult.Failed(exception, null, openedSession?.failureTrace())
        }
        // 진단 자료(트레이스)는 세션을 닫을 때 만들어지므로 닫은 뒤에 붙인다.
        return if (result is AttemptResult.Purchase) result.copy(trace = openedSession?.failureTrace()) else result
    }

    private fun <T> step(stepType: StepType, user: LottoUser, action: () -> T): T = try {
        action()
    } catch (exception: LottoAutomationException) {
        throw exception
    } catch (exception: Exception) {
        throw LottoAutomationException(stepType, user.id, exception)
    }
}
