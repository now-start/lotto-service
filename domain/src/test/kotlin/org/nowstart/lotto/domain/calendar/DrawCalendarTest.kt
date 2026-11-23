package org.nowstart.lotto.domain.calendar

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class DrawCalendarTest {

    private val calendar = DrawCalendar(Clock.systemUTC())

    @Test
    fun `회차별 추첨일은 1회차(2002-12-07)부터 7일 간격이다`() {
        assertThat(calendar.drawDate(1)).isEqualTo(LocalDate.of(2002, 12, 7))
        assertThat(calendar.drawDate(1251)).isEqualTo(LocalDate.of(2026, 11, 21))
        assertThat(calendar.drawDate(1252)).isEqualTo(LocalDate.of(2026, 11, 28))
        assertThatThrownBy { calendar.drawDate(0) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @ParameterizedTest(name = "{0} → 판매 회차 {1}, 판매중={2}, 구매가능={3}")
    @CsvSource(
        // 일요일 09:00 정기 구매 시점
        "2026-11-22T09:00:00, 1252, true, true",
        // 일요일 06:00 이전은 판매 정지(직전 회차 추첨 직후 구간), 대상은 새 회차
        "2026-11-22T05:59:59, 1252, false, false",
        "2026-11-22T06:00:00, 1252, true, true",
        // 평일 자정~06:00 일일 판매 정지
        "2026-11-23T23:59:59, 1252, true, true",
        "2026-11-24T00:00:00, 1252, false, false",
        "2026-11-24T05:59:59, 1252, false, false",
        // 토요일 마감 직전 10분은 판매중이지만 클릭하지 않는다
        "2026-11-28T19:49:59, 1252, true, true",
        "2026-11-28T19:50:00, 1252, true, false",
        "2026-11-28T19:59:59, 1252, true, false",
        // 토요일 20:00 마감 이후는 다음 회차 대상, 판매 정지
        "2026-11-28T20:00:00, 1253, false, false",
        "2026-11-28T23:00:00, 1253, false, false",
    )
    fun `판매 회차와 판매 가능 여부를 판정한다`(localDateTime: String, round: Int, onSale: Boolean, purchasable: Boolean) {
        val at = kst(localDateTime)

        assertThat(calendar.salesRound(at)).isEqualTo(round)
        assertThat(calendar.isOnSale(at)).isEqualTo(onSale)
        assertThat(calendar.isPurchasable(at)).isEqualTo(purchasable)
    }

    @ParameterizedTest(name = "{0} → 다음 구매 가능 시각 {1}")
    @CsvSource(
        "2026-11-23T10:00:00, 2026-11-23T10:00:00",
        "2026-11-24T00:30:00, 2026-11-24T06:00:00",
        "2026-11-28T19:55:00, 2026-11-29T06:00:00",
        "2026-11-28T21:00:00, 2026-11-29T06:00:00",
        "2026-11-29T03:00:00, 2026-11-29T06:00:00",
    )
    fun `다음 구매 가능 시각을 찾는다`(localDateTime: String, expected: String) {
        assertThat(calendar.nextPurchasableAt(kst(localDateTime))).isEqualTo(kst(expected))
    }

    @ParameterizedTest(name = "{0} → 결과가 나온 최신 회차 {1}")
    @CsvSource(
        "2026-11-28T20:59:59, 1251",
        "2026-11-28T21:00:00, 1252",
        "2026-11-29T09:00:00, 1252",
        "2026-11-27T12:00:00, 1251",
    )
    fun `결과가 반영된 최신 회차를 판정한다`(localDateTime: String, round: Int) {
        assertThat(calendar.latestDrawnRound(kst(localDateTime))).isEqualTo(round)
    }

    private fun kst(localDateTime: String): ZonedDateTime =
        LocalDateTime.parse(localDateTime).atZone(DrawCalendar.ZONE)
}
