package org.nowstart.lotto.domain.ledger

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.nowstart.lotto.domain.exception.LedgerUnreadableException

class LedgerMathTest {

    @Test
    fun `회차별 구매 수량은 로또6-45 행의 수량 합이고 상태와 무관하다`() {
        val entries = listOf(
            LedgerEntry.parse("a", "로또6/45", "1252", "2"),
            LedgerEntry.parse("b", "로또6/45", "1252", "1"),
            LedgerEntry.parse("c", "로또6/45", "1251", "5"),
            LedgerEntry.parse("d", "연금복권720+", "-", "-"),
        )

        assertThat(LedgerMath.purchasedCount(entries, 1252)).isEqualTo(3)
        assertThat(LedgerMath.purchasedCount(entries, 1253)).isZero()
        assertThat(LedgerMath.maxRound(entries)).isEqualTo(1252)
        assertThat(LedgerMath.entriesOfRound(entries, 1252).map { it.key }).containsExactly("a", "b")
    }

    @Test
    fun `다른 게임 행만 있으면 로또 구매 수량은 0이다`() {
        val entries = listOf(LedgerEntry.parse("p", "연금복권720+", "290", "1"))

        assertThat(LedgerMath.purchasedCount(entries, 290)).isZero()
        assertThat(LedgerMath.maxRound(entries)).isNull()
    }

    @Test
    fun `클릭 후 새 행은 클릭 전 키에 없던 해당 회차 행만이다`() {
        val existing = LedgerEntry.parse("old", "로또6/45", "1252", "2")
        val fresh = LedgerEntry.parse("new", "로또6/45", "1252", "2")
        val otherRound = LedgerEntry.parse("prev", "로또6/45", "1251", "2")

        val result = LedgerMath.newEntries(setOf("old", "prev"), listOf(existing, fresh, otherRound), 1252)

        assertThat(result).containsExactly(fresh)
    }

    @Test
    fun `로또6-45 행의 회차나 수량을 해석하지 못하면 판독 불가 예외를 던진다`() {
        assertThatThrownBy { LedgerEntry.parse("x", "로또6/45", "회차없음", "5") }
            .isInstanceOf(LedgerUnreadableException::class.java)
        assertThatThrownBy { LedgerEntry.parse("x", "로또 6/45", "1252", "") }
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
