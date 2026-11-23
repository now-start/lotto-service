package org.nowstart.lotto.domain.calendar;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("로또 판매 달력")
class LottoDrawCalendarTest {

    private final LottoDrawCalendar calendar = new LottoDrawCalendar(Clock.systemUTC());

    @Test
    @DisplayName("회차별 추첨일은 1회차(2002-12-07)부터 7일 간격이다")
    void shouldComputeDrawDateFromFirstDraw() {
        then(calendar.drawDate(1)).isEqualTo(LocalDate.of(2002, 12, 7));
        then(calendar.drawDate(1251)).isEqualTo(LocalDate.of(2026, 11, 21));
        then(calendar.drawDate(1252)).isEqualTo(LocalDate.of(2026, 11, 28));
        thenThrownBy(() -> calendar.drawDate(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0} → 판매 회차 {1}, 판매중={2}, 구매가능={3}")
    @CsvSource({
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
            "2026-11-28T23:00:00, 1253, false, false"
    })
    void shouldResolveSalesWindow(String localDateTime, int round, boolean onSale, boolean purchasable) {
        ZonedDateTime at = kst(localDateTime);

        then(calendar.salesRound(at)).isEqualTo(round);
        then(calendar.isOnSale(at)).isEqualTo(onSale);
        then(calendar.isPurchasable(at)).isEqualTo(purchasable);
    }

    @ParameterizedTest(name = "{0} → 다음 구매 가능 시각 {1}")
    @CsvSource({
            "2026-11-23T10:00:00, 2026-11-23T10:00:00",
            "2026-11-24T00:30:00, 2026-11-24T06:00:00",
            "2026-11-28T19:55:00, 2026-11-29T06:00:00",
            "2026-11-28T21:00:00, 2026-11-29T06:00:00",
            "2026-11-29T03:00:00, 2026-11-29T06:00:00"
    })
    void shouldFindNextPurchasableTime(String localDateTime, String expected) {
        then(calendar.nextPurchasableAt(kst(localDateTime))).isEqualTo(kst(expected));
    }

    @ParameterizedTest(name = "{0} → 결과가 나온 최신 회차 {1}")
    @CsvSource({
            "2026-11-28T20:59:59, 1251",
            "2026-11-28T21:00:00, 1252",
            "2026-11-29T09:00:00, 1252",
            "2026-11-27T12:00:00, 1251"
    })
    void shouldResolveLatestDrawnRound(String localDateTime, int round) {
        then(calendar.latestDrawnRound(kst(localDateTime))).isEqualTo(round);
    }

    private ZonedDateTime kst(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(LottoDrawCalendar.ZONE);
    }
}
