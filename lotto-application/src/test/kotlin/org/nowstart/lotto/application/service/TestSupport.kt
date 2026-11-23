package org.nowstart.lotto.application.service

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.nowstart.lotto.application.port.outbound.Attachment
import org.nowstart.lotto.application.port.outbound.LottoAutomationSession
import org.nowstart.lotto.domain.calendar.LottoDrawCalendar

/** 테스트에서 시간을 직접 움직이는 시계. */
class MutableClock(private var now: Instant) : Clock() {

    fun set(instant: Instant) {
        now = instant
    }

    fun advance(duration: Duration) {
        now = now.plus(duration)
    }

    override fun getZone(): ZoneId = LottoDrawCalendar.ZONE

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = now
}

/** 세션 상태 변화를 기록하는 가짜 세션. 닫힌 뒤에만 진단 자료를 돌려준다(실제 세션과 같은 순서). */
class FakeSession(private val traceOnClose: Attachment? = null) : LottoAutomationSession {

    val events = mutableListOf<String>()
    var failed = false
        private set
    var closed = false
        private set

    override fun markFailed() {
        failed = true
        events += "markFailed"
    }

    override fun failureTrace(): Attachment? = if (closed && failed) traceOnClose else null

    override fun close() {
        closed = true
        events += "close"
    }
}
