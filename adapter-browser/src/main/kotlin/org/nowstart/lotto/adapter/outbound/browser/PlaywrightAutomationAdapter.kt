package org.nowstart.lotto.adapter.outbound.browser

import java.time.Duration
import org.nowstart.lotto.application.port.outbound.CheckResult
import org.nowstart.lotto.application.port.outbound.AccountSnapshot
import org.nowstart.lotto.application.port.outbound.AutomationPort
import org.nowstart.lotto.application.port.outbound.AutomationSession
import org.nowstart.lotto.domain.ledger.TicketResult
import org.nowstart.lotto.application.port.outbound.UserAccount
import org.nowstart.lotto.application.port.outbound.PurchaseSubmission
import org.springframework.stereotype.Component

@Component
class PlaywrightAutomationAdapter(
    private val sessionManager: PlaywrightSessionManager,
    private val loginExecutor: PlaywrightLoginExecutor,
    private val purchaseExecutor: PlaywrightPurchaseExecutor,
    private val resultExecutor: PlaywrightResultExecutor,
) : AutomationPort {

    override fun openSession(): AutomationSession = sessionManager.openSession()

    override fun login(session: AutomationSession, user: UserAccount): AccountSnapshot {
        val snapshot = loginExecutor.login(sessionManager.requirePage(session), user)
        // 비밀번호 입력값이 트레이스에 남지 않도록 로그인을 마친 뒤 기록을 시작한다.
        sessionManager.startTracing(session)
        return snapshot
    }

    override fun readAccount(session: AutomationSession): AccountSnapshot =
        loginExecutor.readAccount(sessionManager.requirePage(session))

    override fun readLedger(session: AutomationSession): List<TicketResult> =
        resultExecutor.readLedger(sessionManager.requirePage(session))

    override fun awaitLedger(
        session: AutomationSession,
        condition: (List<TicketResult>) -> Boolean,
        timeout: Duration,
        abortRequested: () -> Boolean,
    ): List<TicketResult>? = resultExecutor.awaitLedger(sessionManager.requirePage(session), condition, timeout, abortRequested)

    override fun captureTickets(session: AutomationSession, rows: List<TicketResult>): List<CheckResult> =
        resultExecutor.captureTickets(sessionManager.requirePage(session), rows)

    override fun buy(session: AutomationSession, user: UserAccount, count: Int, abortRequested: () -> Boolean): PurchaseSubmission =
        purchaseExecutor.buy(sessionManager.requirePage(session), user, count, abortRequested)

    override fun check(session: AutomationSession): List<CheckResult> =
        resultExecutor.check(sessionManager.requirePage(session))
}
