package org.nowstart.lotto.domain.ledger

import org.nowstart.lotto.domain.type.MessageType

/** 구매내역에 표시된 티켓 한 행. 원장 비교와 티켓 조회에 같은 키를 사용한다. */
data class TicketResult(
    val date: String,
    val round: String,
    val name: String,
    val number: String,
    val count: String,
    val result: String,
    val price: String,
) {
    val key: String
        get() = listOf(date, round, name, number, count, result, price)
            .joinToString("|") { it.trim().replace(WHITESPACE, " ") }

    fun isAwaitingDraw(drawRound: Int): Boolean =
        round.toIntOrNull() == drawRound && result == MessageType.WAIT.text

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
