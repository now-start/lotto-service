package org.nowstart.lotto.domain.calendar

import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * 동행복권 로또6/45 판매·추첨 달력(Asia/Seoul).
 *
 * - 추첨: 매주 토요일. 1회차는 2002-12-07이며 이후 7일 간격이다.
 * - 인터넷 판매: 매일 06:00~24:00, 추첨일(토)은 20:00 마감. 토 20:00 ~ 일 06:00은 회차 전환 구간이다.
 * - 결과 확인: 추첨일 21:00 이후를 결과 반영 시점으로 본다.
 *
 * 규칙이 사이트 정책으로 고정돼 있어 설정값으로 두지 않는다.
 */
class DrawCalendar(private val clock: Clock) {

    fun now(): ZonedDateTime = ZonedDateTime.now(clock).withZoneSameInstant(ZONE)

    fun drawDate(round: Int): LocalDate {
        require(round >= 1) { "round must be positive: $round" }
        return FIRST_DRAW_DATE.plusWeeks(round - 1L)
    }

    /** 해당 시각에 판매 중이거나, 판매 정지 구간이면 다음에 판매될 회차. */
    fun salesRound(at: ZonedDateTime): Int {
        val local = at.toZone()
        var nextDraw = local.toLocalDate().with(TemporalAdjusters.nextOrSame(DRAW_DAY))
        if (nextDraw == local.toLocalDate() && !local.toLocalTime().isBefore(DRAW_DAY_SALES_CLOSE)) {
            nextDraw = nextDraw.plusWeeks(1)
        }
        return roundOf(nextDraw)
    }

    /** 사이트가 판매 중인 시각인지. */
    fun isOnSale(at: ZonedDateTime): Boolean {
        val local = at.toZone()
        if (local.toLocalTime().isBefore(DAILY_SALES_OPEN)) {
            return false
        }
        return !(local.dayOfWeek == DRAW_DAY && !local.toLocalTime().isBefore(DRAW_DAY_SALES_CLOSE))
    }

    /** 자동 구매를 시도해도 되는 시각인지(판매 중이고 마감 여유 시간 밖). */
    fun isPurchasable(at: ZonedDateTime): Boolean {
        val local = at.toZone()
        if (!isOnSale(local)) {
            return false
        }
        val cutoff = DRAW_DAY_SALES_CLOSE.minus(PURCHASE_CUTOFF_MARGIN)
        return !(local.dayOfWeek == DRAW_DAY && !local.toLocalTime().isBefore(cutoff))
    }

    /** 해당 회차를 자동 구매할 수 있는 마지막 시각(추첨일 마감 10분 전). */
    fun purchaseDeadline(round: Int): ZonedDateTime =
        drawDate(round).atTime(DRAW_DAY_SALES_CLOSE.minus(PURCHASE_CUTOFF_MARGIN)).atZone(ZONE)

    /** 지금 구매할 수 있으면 그대로, 아니면 다음 판매 시작 시각. */
    fun nextPurchasableAt(at: ZonedDateTime): ZonedDateTime {
        val local = at.toZone()
        if (isPurchasable(local)) {
            return local
        }
        val date = if (local.toLocalTime().isBefore(DAILY_SALES_OPEN)) local.toLocalDate() else local.toLocalDate().plusDays(1)
        return date.atTime(DAILY_SALES_OPEN).atZone(ZONE)
    }

    /** 결과가 반영된 가장 최근 회차. */
    fun latestDrawnRound(at: ZonedDateTime): Int {
        val local = at.toZone()
        var lastDraw = local.toLocalDate().with(TemporalAdjusters.previousOrSame(DRAW_DAY))
        if (lastDraw == local.toLocalDate() && local.toLocalTime().isBefore(RESULT_AVAILABLE)) {
            lastDraw = lastDraw.minusWeeks(1)
        }
        return roundOf(lastDraw)
    }

    private fun roundOf(drawDate: LocalDate): Int =
        Math.toIntExact(ChronoUnit.WEEKS.between(FIRST_DRAW_DATE, drawDate)) + 1

    private fun ZonedDateTime.toZone(): ZonedDateTime = withZoneSameInstant(ZONE)

    companion object {
        @JvmField
        val ZONE: ZoneId = ZoneId.of("Asia/Seoul")

        private val FIRST_DRAW_DATE: LocalDate = LocalDate.of(2002, 12, 7)
        private val DRAW_DAY = DayOfWeek.SATURDAY
        private val DAILY_SALES_OPEN: LocalTime = LocalTime.of(6, 0)
        private val DRAW_DAY_SALES_CLOSE: LocalTime = LocalTime.of(20, 0)

        /** 마감 직전에 클릭이 걸려 결과가 애매해지는 것을 막기 위한 여유 시간. */
        private val PURCHASE_CUTOFF_MARGIN: Duration = Duration.ofMinutes(10)
        private val RESULT_AVAILABLE: LocalTime = LocalTime.of(21, 0)
    }
}
