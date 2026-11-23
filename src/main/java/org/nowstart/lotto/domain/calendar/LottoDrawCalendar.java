package org.nowstart.lotto.domain.calendar;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

/**
 * 동행복권 로또6/45 판매·추첨 달력(Asia/Seoul).
 *
 * <ul>
 *   <li>추첨: 매주 토요일. 1회차는 2002-12-07이며 이후 7일 간격이다.</li>
 *   <li>인터넷 판매: 매일 06:00~24:00, 추첨일(토)은 20:00 마감. 토 20:00 ~ 일 06:00은 회차 전환 구간이다.</li>
 *   <li>결과 확인: 추첨일 21:00 이후를 결과 반영 시점으로 본다.</li>
 * </ul>
 * 규칙이 사이트 정책으로 고정돼 있어 설정값으로 두지 않는다.
 */
public final class LottoDrawCalendar {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private static final LocalDate FIRST_DRAW_DATE = LocalDate.of(2002, 12, 7);
    private static final DayOfWeek DRAW_DAY = DayOfWeek.SATURDAY;
    private static final LocalTime DAILY_SALES_OPEN = LocalTime.of(6, 0);
    private static final LocalTime DRAW_DAY_SALES_CLOSE = LocalTime.of(20, 0);
    /** 마감 직전에 클릭이 걸려 결과가 애매해지는 것을 막기 위한 여유 시간. */
    private static final Duration PURCHASE_CUTOFF_MARGIN = Duration.ofMinutes(10);
    private static final LocalTime RESULT_AVAILABLE = LocalTime.of(21, 0);

    private final Clock clock;

    public LottoDrawCalendar(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public ZonedDateTime now() {
        return ZonedDateTime.now(clock).withZoneSameInstant(ZONE);
    }

    public LocalDate drawDate(int round) {
        if (round < 1) {
            throw new IllegalArgumentException("round must be positive: " + round);
        }
        return FIRST_DRAW_DATE.plusWeeks(round - 1L);
    }

    /** 해당 시각에 판매 중이거나, 판매 정지 구간이면 다음에 판매될 회차. */
    public int salesRound(ZonedDateTime at) {
        ZonedDateTime local = toZone(at);
        LocalDate nextDraw = local.toLocalDate().with(TemporalAdjusters.nextOrSame(DRAW_DAY));
        if (nextDraw.equals(local.toLocalDate()) && !local.toLocalTime().isBefore(DRAW_DAY_SALES_CLOSE)) {
            nextDraw = nextDraw.plusWeeks(1);
        }
        return roundOf(nextDraw);
    }

    /** 사이트가 판매 중인 시각인지. */
    public boolean isOnSale(ZonedDateTime at) {
        ZonedDateTime local = toZone(at);
        if (local.toLocalTime().isBefore(DAILY_SALES_OPEN)) {
            return false;
        }
        return !(local.getDayOfWeek() == DRAW_DAY && !local.toLocalTime().isBefore(DRAW_DAY_SALES_CLOSE));
    }

    /** 자동 구매를 시도해도 되는 시각인지(판매 중이고 마감 여유 시간 밖). */
    public boolean isPurchasable(ZonedDateTime at) {
        ZonedDateTime local = toZone(at);
        if (!isOnSale(local)) {
            return false;
        }
        LocalTime cutoff = DRAW_DAY_SALES_CLOSE.minus(PURCHASE_CUTOFF_MARGIN);
        return !(local.getDayOfWeek() == DRAW_DAY && !local.toLocalTime().isBefore(cutoff));
    }

    /** 지금 구매할 수 있으면 그대로, 아니면 다음 판매 시작 시각. */
    public ZonedDateTime nextPurchasableAt(ZonedDateTime at) {
        ZonedDateTime local = toZone(at);
        if (isPurchasable(local)) {
            return local;
        }
        LocalDate date = local.toLocalDate();
        if (!local.toLocalTime().isBefore(DAILY_SALES_OPEN)) {
            date = date.plusDays(1);
        }
        return date.atTime(DAILY_SALES_OPEN).atZone(ZONE);
    }

    /** 결과가 반영된 가장 최근 회차. */
    public int latestDrawnRound(ZonedDateTime at) {
        ZonedDateTime local = toZone(at);
        LocalDate lastDraw = local.toLocalDate().with(TemporalAdjusters.previousOrSame(DRAW_DAY));
        if (lastDraw.equals(local.toLocalDate()) && local.toLocalTime().isBefore(RESULT_AVAILABLE)) {
            lastDraw = lastDraw.minusWeeks(1);
        }
        return roundOf(lastDraw);
    }

    private int roundOf(LocalDate drawDate) {
        return Math.toIntExact(ChronoUnit.WEEKS.between(FIRST_DRAW_DATE, drawDate)) + 1;
    }

    private ZonedDateTime toZone(ZonedDateTime at) {
        return Objects.requireNonNull(at, "at must not be null").withZoneSameInstant(ZONE);
    }
}
