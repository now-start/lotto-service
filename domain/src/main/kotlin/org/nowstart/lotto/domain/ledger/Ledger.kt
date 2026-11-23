package org.nowstart.lotto.domain.ledger

import org.nowstart.lotto.domain.exception.LedgerUnreadableException

/**
 * 구매내역(원장) 한 행에서 구매 판단에 필요한 값만 담는다.
 *
 * @property ticket 구매내역에 표시된 원본 행
 * @property round 회차. 로또6/45가 아닌 행은 0
 * @property count 구매 수량. 로또6/45가 아닌 행은 0
 */
data class LedgerEntry(val ticket: TicketResult, val round: Int, val count: Int) {

    val isLotto645: Boolean get() = isLotto645Name(ticket.name)

    companion object {
        const val LOTTO_645 = "로또6/45"

        /**
         * 원장 행 문자열을 해석한다. 로또6/45 행의 회차·수량이 숫자가 아니면 판독 불가로 본다.
         * 다른 게임 행은 구매 판단에 쓰지 않으므로 숫자 해석을 요구하지 않는다.
         */
        fun parse(ticket: TicketResult): LedgerEntry {
            if (!isLotto645Name(ticket.name)) {
                return LedgerEntry(ticket, 0, 0)
            }
            return LedgerEntry(ticket, parsePositive("회차", ticket.round), parsePositive("수량", ticket.count))
        }

        private fun isLotto645Name(gameName: String): Boolean =
            gameName.replace(Regex("\\s+"), "").contains(LOTTO_645)

        private fun parsePositive(field: String, text: String): Int {
            val digits = text.replace(Regex("\\D"), "")
            if (digits.isEmpty() || digits.length > 9) {
                throw LedgerUnreadableException("로또6/45 원장 행의 ${field}를 해석하지 못했습니다: '$text'")
            }
            val value = digits.toInt()
            if (value <= 0) {
                throw LedgerUnreadableException("로또6/45 원장 행의 ${field}가 0 이하입니다: '$text'")
            }
            return value
        }
    }
}

/** 원장 행으로 구매 수량을 계산한다. 로또6/45 행만 대상으로 한다. */
object LedgerMath {

    /** 해당 회차에 이미 구매한 수량 합. 상태(미추첨 등)는 보지 않는다. */
    fun purchasedCount(entries: Collection<LedgerEntry>, round: Int): Int =
        entriesOfRound(entries, round).sumOf { it.count }

    /** 원장에 보이는 가장 큰 로또6/45 회차. */
    fun maxRound(entries: Collection<LedgerEntry>): Int? =
        entries.filter { it.isLotto645 }.maxOfOrNull { it.round }

    /** 클릭 전에 없던 해당 회차의 로또6/45 행. */
    fun newEntries(keysBefore: Set<String>, after: Collection<TicketResult>, round: Int): List<LedgerEntry> {
        val newRows = after.filterNot { it.key in keysBefore }
        return entriesOfRound(newRows.map(LedgerEntry::parse), round)
    }

    fun entriesOfRound(entries: Collection<LedgerEntry>, round: Int): List<LedgerEntry> =
        entries.filter { it.isLotto645 && it.round == round }
}

/** 최종 확정 클릭 이후 관찰한 증거로 판정한 구매 결과. */
enum class PurchaseConfirmation { NEW_LEDGER_ENTRY, DEPOSIT_DEBITED, SITE_REJECTED, UNCONFIRMED }

/**
 * 한 회차의 구매 계획. 회차당 인터넷 구매 한도(5게임)와 이미 산 수량을 반영해 이번에 살 수량을 정한다.
 *
 * @property toBuy 이번에 살 수량(0이면 구매하지 않음)
 */
data class PurchasePlan(val round: Int, val targetCount: Int, val alreadyPurchased: Int, val toBuy: Int) {

    val isComplete: Boolean get() = toBuy == 0

    val requiredDepositWon: Long get() = toBuy * PRICE_PER_GAME_WON

    fun hasInsufficientDeposit(availableWon: Long?): Boolean =
        availableWon != null && availableWon < requiredDepositWon

    fun confirmationAfterClick(
        hasNewLedgerEntry: Boolean,
        depositBefore: Long?,
        depositAfter: Long?,
        siteRejected: Boolean,
    ): PurchaseConfirmation = when {
        hasNewLedgerEntry -> PurchaseConfirmation.NEW_LEDGER_ENTRY
        !isComplete && depositBefore != null && depositAfter != null && depositBefore - depositAfter >= requiredDepositWon ->
            PurchaseConfirmation.DEPOSIT_DEBITED
        siteRejected -> PurchaseConfirmation.SITE_REJECTED
        else -> PurchaseConfirmation.UNCONFIRMED
    }

    companion object {
        const val MAX_GAMES_PER_ROUND = 5
        const val PRICE_PER_GAME_WON = 1_000L

        fun of(round: Int, targetCount: Int, alreadyPurchased: Int): PurchasePlan {
            require(round >= 1) { "round must be positive: $round" }
            require(targetCount >= 1) { "targetCount must be positive: $targetCount" }
            require(alreadyPurchased >= 0) { "alreadyPurchased must not be negative: $alreadyPurchased" }
            val target = minOf(targetCount, MAX_GAMES_PER_ROUND)
            return PurchasePlan(round, targetCount, alreadyPurchased, maxOf(0, target - alreadyPurchased))
        }
    }
}

/** 화면에 표시된 금액 문자열("60,000원")을 원 단위 정수로 바꾼다. */
object Won {

    fun parse(text: String?): Long? {
        val digits = text.orEmpty().replace(Regex("\\D"), "")
        if (digits.isEmpty() || digits.length > 15) {
            return null
        }
        return digits.toLong()
    }
}
