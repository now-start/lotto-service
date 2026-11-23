package org.nowstart.lotto.domain.ledger

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.nowstart.lotto.domain.exception.LedgerUnreadableException

class LedgerMathTest {

    private fun ticket(number: String, round: String, count: String, game: String = "로또6/45") =
        TicketResult("2026-11-23", round, game, number, count, "미추첨", "1,000")

    @Test
    fun `회차별 구매 수량은 로또6-45 행의 수량 합이고 상태와 무관하다`() {
        val entries = listOf(
            ticket("a", "1252", "2"),
            ticket("b", "1252", "1"),
            ticket("c", "1251", "5"),
            ticket("d", "-", "-", "연금복권720+"),
        ).map(LedgerEntry::parse)

        assertThat(LedgerMath.purchasedCount(entries, 1252)).isEqualTo(3)
        assertThat(LedgerMath.purchasedCount(entries, 1253)).isZero()
        assertThat(LedgerMath.maxRound(entries)).isEqualTo(1252)
        assertThat(LedgerMath.entriesOfRound(entries, 1252).map { it.ticket.number }).containsExactly("a", "b")
    }

    @Test
    fun `다른 게임 행만 있으면 로또 구매 수량은 0이다`() {
        val entries = listOf(LedgerEntry.parse(ticket("p", "290", "1", "연금복권720+")))

        assertThat(LedgerMath.purchasedCount(entries, 290)).isZero()
        assertThat(LedgerMath.maxRound(entries)).isNull()
    }

    @Test
    fun `클릭 후 새 행은 클릭 전 키에 없던 해당 회차 행만이다`() {
        val existing = ticket("old", "1252", "2")
        val fresh = ticket("new", "1252", "2")
        val anotherFresh = ticket("newer", "1252회", "1장")
        val otherRound = ticket("prev", "1251", "2")
        val otherGame = ticket("p", "1252", "1", "연금복권720+")
        val rows = listOf(otherGame, existing, anotherFresh, otherRound, fresh)

        val result = LedgerMath.newEntries(setOf(existing.key), rows, 1252)

        assertThat(result.map { it.ticket }).containsExactly(anotherFresh, fresh)
        assertThat(result.sumOf { it.count }).isEqualTo(3)
    }

    @Test
    fun `알려진 키는 해석하지 않고 새 행의 수량은 반드시 검증한다`() {
        val invalid = ticket("기존", "1252", "-")
        val fresh = ticket("신규", "1252", "1")
        val rows = listOf(invalid, fresh)

        assertThat(LedgerMath.newEntries(setOf(invalid.key), rows, 1252).map { it.ticket }).containsExactly(fresh)
        assertThatThrownBy { LedgerMath.newEntries(emptySet(), rows, 1252) }
            .isInstanceOf(LedgerUnreadableException::class.java)
    }

    @Test
    fun `결과 대기는 숫자 회차가 일치하고 미추첨인 티켓에만 해당한다`() {
        val pending = ticket("a", "1252", "1")

        assertThat(pending.isAwaitingDraw(1252)).isTrue()
        assertThat(pending.isAwaitingDraw(1251)).isFalse()
        assertThat(pending.copy(round = "-").isAwaitingDraw(1252)).isFalse()
        assertThat(pending.copy(result = "낙첨").isAwaitingDraw(1252)).isFalse()
    }

    @Test
    fun `로또6-45 행의 회차나 수량을 해석하지 못하면 판독 불가 예외를 던진다`() {
        assertThatThrownBy { LedgerEntry.parse(ticket("x", "회차없음", "5")) }
            .isInstanceOf(LedgerUnreadableException::class.java)
        assertThatThrownBy { LedgerEntry.parse(ticket("x", "1252", "", "로또 6/45")) }
            .isInstanceOf(LedgerUnreadableException::class.java)
    }
}

class PurchasePlanTest {

    @ParameterizedTest(name = "목표 {0}, 이미 {1} → 이번 {2}")
    @CsvSource(
        "5, 0, 5", // 신규 계정(빈 원장)
        "5, 2, 3", // 일부만 산 회차는 모자란 수량만
        "5, 5, 0", // 이미 다 샀으면 사지 않음 (2026-11-23 사례)
        "3, 5, 0",
        "1, 0, 1",
        "5, 7, 0", // 원장이 한도보다 많아도 음수가 되지 않음
    )
    fun `회차 한도 안에서 모자란 수량만 산다`(target: Int, already: Int, toBuy: Int) {
        val plan = PurchasePlan.of(1252, target, already)

        assertThat(plan.toBuy).isEqualTo(toBuy)
        assertThat(plan.isComplete).isEqualTo(toBuy == 0)
        assertThat(plan.requiredDepositWon).isEqualTo(toBuy * 1_000L)
    }

    @Test
    fun `알려진 예치금이 필요한 금액 미만일 때만 부족으로 판정한다`() {
        val plan = PurchasePlan.of(1252, 5, 3)
        assertThat(plan.hasInsufficientDeposit(1_999)).isTrue()
        assertThat(plan.hasInsufficientDeposit(2_000)).isFalse()
        assertThat(plan.hasInsufficientDeposit(null)).isFalse()
    }

    @ParameterizedTest
    @CsvSource(
        "true,  10000, 10000, true,  NEW_LEDGER_ENTRY",
        "false, 10000, 8000,  true,  DEPOSIT_DEBITED",
        "false, 10000, 7999,  false, DEPOSIT_DEBITED",
        "false, 10000, 8001,  true,  SITE_REJECTED",
        "false, 10000, 8001,  false, UNCONFIRMED",
        "false, 10000, ,      false, UNCONFIRMED",
        "false, ,      8000,  false, UNCONFIRMED",
    )
    fun `구매 증거는 원장 새 행, 필요 금액 이상 차감, 사이트 거부 순으로 판정한다`(
        newEntry: Boolean, before: Long?, after: Long?, rejected: Boolean, expected: PurchaseConfirmation,
    ) {
        val plan = PurchasePlan.of(1252, 5, 3)
        assertThat(plan.confirmationAfterClick(newEntry, before, after, rejected)).isEqualTo(expected)
    }

    @Test
    fun `잘못된 입력은 거부한다`() {
        assertThatThrownBy { PurchasePlan.of(0, 5, 0) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { PurchasePlan.of(1252, 0, 0) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { PurchasePlan.of(1252, 5, -1) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `금액 문자열을 원 단위로 해석한다`() {
        assertThat(Won.parse("60,000원")).isEqualTo(60_000L)
        assertThat(Won.parse("108,818")).isEqualTo(108_818L)
        assertThat(Won.parse("")).isNull()
        assertThat(Won.parse(null)).isNull()
    }
}
