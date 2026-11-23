package org.nowstart.lotto.application.service

import java.time.Duration
import org.nowstart.lotto.application.port.outbound.CheckResult
import org.nowstart.lotto.application.port.outbound.AccountSnapshot
import org.nowstart.lotto.application.port.outbound.AutomationPort
import org.nowstart.lotto.application.port.outbound.AutomationSession
import org.nowstart.lotto.domain.ledger.TicketResult
import org.nowstart.lotto.application.port.outbound.UserAccount
import org.nowstart.lotto.application.port.outbound.PurchaseSubmission
import org.nowstart.lotto.domain.calendar.DrawCalendar
import org.nowstart.lotto.domain.ledger.LedgerEntry
import org.nowstart.lotto.domain.ledger.LedgerMath
import org.nowstart.lotto.domain.ledger.PurchaseConfirmation
import org.nowstart.lotto.domain.ledger.PurchasePlan
import org.nowstart.lotto.domain.ledger.Won
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

private val log = LoggerFactory.getLogger(PurchaseProcedure::class.java)

/**
 * 멱등한 구매 절차: 원장으로 이번 회차 구매 수량을 먼저 확인하고, 모자란 수량만 산다.
 *
 * 안전 규칙
 * - 원장을 확실히 읽지 못하면 사지 않는다(판독 불가 예외가 그대로 올라간다).
 * - 확정 클릭 뒤에는 긍정 증거(원장 새 행, 예치금 차감, 사이트 거부 문구)만 결과를 정한다. 없으면 [PurchaseOutcome.Uncertain].
 * - 구매가 확인된 뒤에는 티켓 캡처가 실패해도 성공이다.
 * - 판정 대기 중인 (계정, 회차)는 원장 반영 지연 시간 동안 다시 사지 않는다.
 */
@Service
class PurchaseProcedure(
    private val automationPort: AutomationPort,
    private val drawCalendar: DrawCalendar,
    private val uncertainPurchaseRegistry: UncertainPurchaseRegistry,
) {

    /** 브라우저를 열기 전에 판단할 수 있는 거부 사유(판매 시간, 판정 대기). 없으면 null. */
    fun precheck(user: UserAccount, round: Int): PurchaseOutcome? {
        val now = drawCalendar.now()
        if (drawCalendar.salesRound(now) != round) {
            return PurchaseOutcome.Refused("${round}회차는 현재 판매 회차가 아니므로 구매하지 않았습니다.")
        }
        if (!drawCalendar.isPurchasable(now)) {
            val next = drawCalendar.nextPurchasableAt(now)
            return PurchaseOutcome.NotOnSale(next, drawCalendar.salesRound(next))
        }
        return uncertainPurchaseRegistry.blockedUntil(user.id, round)?.let { PurchaseOutcome.Blocked(round, it) }
    }

    fun purchase(
        session: AutomationSession,
        user: UserAccount,
        account: AccountSnapshot,
        round: Int,
        abortRequested: () -> Boolean,
    ): PurchaseOutcome {
        // 로그인에 시간이 걸렸을 수 있으므로 판매 시간·판정 대기를 다시 확인한다.
        precheck(user, round)?.let { return it }

        val before = automationPort.readLedger(session)
        val beforeEntries = before.map(LedgerEntry::parse)
        val maxRound = LedgerMath.maxRound(beforeEntries)
        if (maxRound != null && maxRound > round) {
            return PurchaseOutcome.Refused("원장에 계산된 판매 회차(${round})보다 큰 회차(${maxRound})가 있습니다. 회차 계산을 점검해야 합니다.")
        }

        val plan = PurchasePlan.of(round, user.count, LedgerMath.purchasedCount(beforeEntries, round))
        log.info("[Purchase][{}] Plan round={} target={} already={} toBuy={}",
            user.id, plan.round, plan.targetCount, plan.alreadyPurchased, plan.toBuy)
        if (plan.isComplete) {
            val tickets = LedgerMath.entriesOfRound(beforeEntries, round).map { it.ticket }
            return PurchaseOutcome.AlreadyPurchased(plan, captureQuietly(session, tickets))
        }

        val depositBefore = Won.parse(account.deposit)
        if (plan.hasInsufficientDeposit(depositBefore)) {
            return PurchaseOutcome.Refused("예치금이 부족합니다: 보유 ${depositBefore}원, 필요 ${plan.requiredDepositWon}원 (${plan.toBuy}장)")
        }
        var purchaseHold: PurchaseOutcome? = null
        val stopBeforeClick = {
            purchaseHold = precheck(user, round)
            purchaseHold != null || abortRequested()
        }
        if (stopBeforeClick()) {
            return purchaseHold ?: PurchaseOutcome.Aborted("구매 화면 진입 전")
        }

        val submission = automationPort.buy(session, user, plan.toBuy, stopBeforeClick)
        return when (submission.status) {
            PurchaseSubmission.Status.NOT_CLICKED -> purchaseHold ?: PurchaseOutcome.Aborted("최종 확정 전")
            PurchaseSubmission.Status.DRY_RUN -> PurchaseOutcome.DryRun(plan)
            PurchaseSubmission.Status.CLICKED -> try {
                verifyAfterClick(session, user, plan, before, depositBefore, submission, abortRequested)
            } catch (exception: RuntimeException) {
                log.error("[Purchase][{}] Verification failed after final click - treating as uncertain", user.id, exception)
                PurchaseOutcome.Uncertain(plan, uncertainPurchaseRegistry.record(user.id, plan.round))
            }
        }
    }

    private fun verifyAfterClick(
        session: AutomationSession,
        user: UserAccount,
        plan: PurchasePlan,
        before: List<TicketResult>,
        depositBefore: Long?,
        submission: PurchaseSubmission,
        abortRequested: () -> Boolean,
    ): PurchaseOutcome {
        val keysBefore = before.map { it.key }.toSet()
        val after = automationPort.awaitLedger(
            session,
            { rows -> LedgerMath.newEntries(keysBefore, rows, plan.round).isNotEmpty() },
            VERIFY_TIMEOUT,
            abortRequested,
        )
        val fresh = after?.let { LedgerMath.newEntries(keysBefore, it, plan.round) }.orEmpty()
        val depositAfter = if (fresh.isEmpty() && depositBefore != null) readDeposit(session, user) else null
        val confirmation = plan.confirmationAfterClick(
            hasNewLedgerEntry = fresh.isNotEmpty(),
            depositBefore = depositBefore,
            depositAfter = depositAfter,
            siteRejected = submission.rejectionMessages.isNotEmpty(),
        )
        return when (confirmation) {
            PurchaseConfirmation.NEW_LEDGER_ENTRY -> {
                val freshCount = fresh.sumOf { it.count }
                if (freshCount != plan.toBuy) {
                    log.warn("[Purchase][{}] New rows count {} differs from requested {}", user.id, freshCount, plan.toBuy)
                }
                PurchaseOutcome.Purchased(plan, captureQuietly(session, fresh.map { it.ticket }))
            }
            PurchaseConfirmation.DEPOSIT_DEBITED -> {
                uncertainPurchaseRegistry.record(user.id, plan.round)
                PurchaseOutcome.Purchased(
                    plan,
                    emptyList(),
                    "원장 반영이 늦어 예치금 차감으로 구매를 확인했습니다. 티켓은 사이트 구매내역에서 확인하세요.",
                )
            }
            PurchaseConfirmation.SITE_REJECTED -> {
                uncertainPurchaseRegistry.record(user.id, plan.round)
                PurchaseOutcome.Rejected(plan, submission.rejectionMessages)
            }
            PurchaseConfirmation.UNCONFIRMED ->
                PurchaseOutcome.Uncertain(plan, uncertainPurchaseRegistry.record(user.id, plan.round))
        }
    }

    private fun readDeposit(session: AutomationSession, user: UserAccount): Long? = try {
        Won.parse(automationPort.readAccount(session).deposit)
    } catch (exception: RuntimeException) {
        log.warn("[Purchase][{}] Deposit re-read failed", user.id, exception)
        null
    }

    /** 구매 판정은 끝났으므로 티켓 캡처 실패는 결과를 바꾸지 않는다. */
    private fun captureQuietly(session: AutomationSession, rows: List<TicketResult>): List<CheckResult> = try {
        automationPort.captureTickets(session, rows)
    } catch (exception: RuntimeException) {
        log.warn("[Purchase] Ticket capture failed - continuing without images", exception)
        rows.map { CheckResult(it, null) }
    }

    companion object {
        /** 확정 클릭 후 원장에 새 행이 보이기를 기다리는 시간. */
        val VERIFY_TIMEOUT: Duration = Duration.ofSeconds(60)

    }
}
