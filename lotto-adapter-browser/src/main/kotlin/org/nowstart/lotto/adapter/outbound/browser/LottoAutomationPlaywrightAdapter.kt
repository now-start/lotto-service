package org.nowstart.lotto.adapter.outbound.browser

import java.time.Duration
import org.nowstart.lotto.application.port.outbound.CheckResult
import org.nowstart.lotto.application.port.outbound.LottoAccountSnapshot
import org.nowstart.lotto.application.port.outbound.LottoAutomationPort
import org.nowstart.lotto.application.port.outbound.LottoAutomationSession
import org.nowstart.lotto.application.port.outbound.LottoResult
import org.nowstart.lotto.application.port.outbound.LottoUser
import org.nowstart.lotto.application.port.outbound.PurchaseSubmission
import org.springframework.stereotype.Component

@Component
class LottoAutomationPlaywrightAdapter(
    private val sessionManager: PlaywrightSessionManager,
    private val loginExecutor: PlaywrightLoginExecutor,
    private val purchaseExecutor: PlaywrightPurchaseExecutor,
    private val resultExecutor: PlaywrightResultExecutor,
) : LottoAutomationPort {

    override fun openSession(): LottoAutomationSession = sessionManager.openSession()

    override fun login(session: LottoAutomationSession, user: LottoUser): LottoAccountSnapshot {
        val snapshot = loginExecutor.login(sessionManager.requirePage(session), user)
        // 비밀번호 입력값이 트레이스에 남지 않도록 로그인을 마친 뒤 기록을 시작한다.
        sessionManager.startTracing(session)
        return snapshot
    }

    override fun readAccount(session: LottoAutomationSession): LottoAccountSnapshot =
        loginExecutor.readAccount(sessionManager.requirePage(session))

    override fun readLedger(session: LottoAutomationSession): List<LottoResult> =
        resultExecutor.readLedger(sessionManager.requirePage(session))

    override fun awaitLedger(
        session: LottoAutomationSession,
        condition: (List<LottoResult>) -> Boolean,
        timeout: Duration,
        abortRequested: () -> Boolean,
    ): List<LottoResult>? = resultExecutor.awaitLedger(sessionManager.requirePage(session), condition, timeout, abortRequested)

    override fun captureTickets(session: LottoAutomationSession, rows: List<LottoResult>): List<CheckResult> =
        resultExecutor.captureTickets(sessionManager.requirePage(session), rows)

    override fun buy(session: LottoAutomationSession, user: LottoUser, count: Int, abortRequested: () -> Boolean): PurchaseSubmission =
        purchaseExecutor.buy(sessionManager.requirePage(session), user, count, abortRequested)

    override fun check(session: LottoAutomationSession): List<CheckResult> =
        resultExecutor.check(sessionManager.requirePage(session))
}
