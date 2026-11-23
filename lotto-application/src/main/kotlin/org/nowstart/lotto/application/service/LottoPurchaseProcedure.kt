package org.nowstart.lotto.application.service

import java.time.Duration
import org.nowstart.lotto.application.port.outbound.CheckResult
import org.nowstart.lotto.application.port.outbound.LottoAccountSnapshot
import org.nowstart.lotto.application.port.outbound.LottoAutomationPort
import org.nowstart.lotto.application.port.outbound.LottoAutomationSession
import org.nowstart.lotto.application.port.outbound.LottoResult
import org.nowstart.lotto.application.port.outbound.LottoUser
import org.nowstart.lotto.application.port.outbound.PurchaseSubmission
import org.nowstart.lotto.domain.calendar.LottoDrawCalendar
import org.nowstart.lotto.domain.ledger.LedgerEntry
import org.nowstart.lotto.domain.ledger.LedgerMath
import org.nowstart.lotto.domain.ledger.PurchasePlan
import org.nowstart.lotto.domain.ledger.Won
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

private val log = LoggerFactory.getLogger(LottoPurchaseProcedure::class.java)

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
class LottoPurchaseProcedure(
    private val lottoAutomationPort: LottoAutomationPort,
    private val lottoDrawCalendar: LottoDrawCalendar,
    private val uncertainPurchaseRegistry: UncertainPurchaseRegistry,
) {

    /** 브라우저를 열기 전에 판단할 수 있는 거부 사유(판매 시간, 판정 대기). 없으면 null. */
    fun precheck(user: LottoUser): PurchaseOutcome? {
        val now = lottoDrawCalendar.now()
        if (!lottoDrawCalendar.isPurchasable(now)) {
            val next = lottoDrawCalendar.nextPurchasableAt(now)
            return PurchaseOutcome.NotOnSale(next, lottoDrawCalendar.salesRound(next))
        }
        val round = lottoDrawCalendar.salesRound(now)
        return uncertainPurchaseRegistry.blockedUntil(user.id, round)?.let { PurchaseOutcome.Blocked(round, it) }
    }

    fun purchase(
        session: LottoAutomationSession,
        user: LottoUser,
        account: LottoAccountSnapshot,
        abortRequested: () -> Boolean,
    ): PurchaseOutcome {
        // 로그인에 시간이 걸렸을 수 있으므로 판매 시간·판정 대기를 다시 확인한다.
        precheck(user)?.let { return it }
        val round = lottoDrawCalendar.salesRound(lottoDrawCalendar.now())

        val before = lottoAutomationPort.readLedger(session)
        val beforeEntries = before.map(::toEntry)
        val maxRound = LedgerMath.maxRound(beforeEntries)
        if (maxRound != null && maxRound > round) {
            return PurchaseOutcome.Refused("원장에 계산된 판매 회차(${round})보다 큰 회차(${maxRound})가 있습니다. 회차 계산을 점검해야 합니다.")
        }

        val plan = PurchasePlan.of(round, user.count, LedgerMath.purchasedCount(beforeEntries, round))
        log.info("[Purchase][{}] Plan round={} target={} already={} toBuy={}",
            user.id, plan.round, plan.targetCount, plan.alreadyPurchased, plan.toBuy)
        if (plan.isComplete) {
            return PurchaseOutcome.AlreadyPurchased(plan, captureQuietly(session, before.filter { isLottoOfRound(it, round) }))
        }

        val depositBefore = Won.parse(account.deposit)
        if (depositBefore != null && depositBefore < plan.requiredDepositWon) {
            return PurchaseOutcome.Refused("예치금이 부족합니다: 보유 ${depositBefore}원, 필요 ${plan.requiredDepositWon}원 (${plan.toBuy}장)")
        }
        if (abortRequested()) {
            return PurchaseOutcome.Aborted("구매 화면 진입 전")
        }

        val submission = lottoAutomationPort.buy(session, user, plan.toBuy, abortRequested)
        return when (submission.status) {
            PurchaseSubmission.Status.NOT_CLICKED -> PurchaseOutcome.Aborted("최종 확정 전")
            PurchaseSubmission.Status.DRY_RUN -> PurchaseOutcome.DryRun(plan)
            PurchaseSubmission.Status.CLICKED -> verifySafely(session, user, plan, before, depositBefore, submission, abortRequested)
        }
    }

    /** 클릭 이후의 어떤 예외도 '구매 실패'로 바꾸지 않는다: 판정 불가로 보고 재구매를 보류한다. */
    private fun verifySafely(
        session: LottoAutomationSession,
        user: LottoUser,
        plan: PurchasePlan,
        before: List<LottoResult>,
        depositBefore: Long?,
        submission: PurchaseSubmission,
        abortRequested: () -> Boolean,
    ): PurchaseOutcome = try {
        verify(session, user, plan, before, depositBefore, submission, abortRequested)
    } catch (exception: RuntimeException) {
        log.error("[Purchase][{}] Verification failed after final click - treating as uncertain", user.id, exception)
        PurchaseOutcome.Uncertain(plan, uncertainPurchaseRegistry.record(user.id, plan.round))
    }

    private fun verify(
        session: LottoAutomationSession,
        user: LottoUser,
        plan: PurchasePlan,
        before: List<LottoResult>,
        depositBefore: Long?,
        submission: PurchaseSubmission,
        abortRequested: () -> Boolean,
    ): PurchaseOutcome {
        val keysBefore = before.map { it.key }.toSet()
        val after = lottoAutomationPort.awaitLedger(
            session,
            { rows -> newRowsOfRound(keysBefore, rows, plan.round).isNotEmpty() },
            VERIFY_TIMEOUT,
            abortRequested,
        )
        if (after != null) {
            val fresh = newRowsOfRound(keysBefore, after, plan.round)
            val freshCount = fresh.map(::toEntry).sumOf { it.count }
            if (freshCount != plan.toBuy) {
                log.warn("[Purchase][{}] New rows count {} differs from requested {}", user.id, freshCount, plan.toBuy)
            }
            return PurchaseOutcome.Purchased(plan, captureQuietly(session, fresh))
        }

        // 아래 결과들은 원장에 아직 행이 없으므로, 반영 지연 동안 같은 회차 재구매를 막는다.
        if (depositDropped(session, user, depositBefore, plan)) {
            uncertainPurchaseRegistry.record(user.id, plan.round)
            return PurchaseOutcome.Purchased(
                plan,
                emptyList(),
                "원장 반영이 늦어 예치금 차감으로 구매를 확인했습니다. 티켓은 사이트 구매내역에서 확인하세요.",
            )
        }

        val rejections = submission.dialogMessages.filter { message -> REJECTION_HINTS.any { message.contains(it) } }
        if (rejections.isNotEmpty()) {
            uncertainPurchaseRegistry.record(user.id, plan.round)
            return PurchaseOutcome.Rejected(plan, rejections)
        }

        return PurchaseOutcome.Uncertain(plan, uncertainPurchaseRegistry.record(user.id, plan.round))
    }

    private fun depositDropped(
        session: LottoAutomationSession,
        user: LottoUser,
        depositBefore: Long?,
        plan: PurchasePlan,
    ): Boolean {
        if (depositBefore == null) {
            return false
        }
        return try {
            val depositAfter = Won.parse(lottoAutomationPort.readAccount(session).deposit)
            depositAfter != null && depositBefore - depositAfter >= plan.requiredDepositWon
        } catch (exception: RuntimeException) {
            log.warn("[Purchase][{}] Deposit re-read failed", user.id, exception)
            false
        }
    }

    /** 구매 판정은 끝났으므로 티켓 캡처 실패는 결과를 바꾸지 않는다. */
    private fun captureQuietly(session: LottoAutomationSession, rows: List<LottoResult>): List<CheckResult> = try {
        lottoAutomationPort.captureTickets(session, rows)
    } catch (exception: RuntimeException) {
        log.warn("[Purchase] Ticket capture failed - continuing without images", exception)
        rows.map { CheckResult(it, null) }
    }

    private fun newRowsOfRound(keysBefore: Set<String>, rows: List<LottoResult>, round: Int): List<LottoResult> =
        rows.filter { it.key !in keysBefore && isLottoOfRound(it, round) }

    private fun isLottoOfRound(row: LottoResult, round: Int): Boolean {
        val entry = toEntry(row)
        return entry.isLotto645 && entry.round == round
    }

    private fun toEntry(row: LottoResult): LedgerEntry = LedgerEntry.parse(row.key, row.name, row.round, row.count)

    companion object {
        /** 확정 클릭 후 원장에 새 행이 보이기를 기다리는 시간. */
        val VERIFY_TIMEOUT: Duration = Duration.ofSeconds(60)

        /**
         * 사이트가 구매를 거부할 때 띄우는 대화상자 문구의 단서. 성공 안내에도 나오는 단어("예치금", "잔액")만으로는 거부로 보지 않는다.
         * 오인하더라도 거부 결과는 재구매 보류를 걸기 때문에 이중 구매로 이어지지 않는다.
         */
        private val REJECTION_HINTS = listOf("한도", "판매시간", "판매 시간", "구매할 수 없", "구매가 불가", "잔액이 부족", "예치금이 부족")
    }
}
