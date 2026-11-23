package org.nowstart.lotto.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.nowstart.lotto.application.port.outbound.CheckResult
import org.nowstart.lotto.application.port.outbound.LottoAccountSnapshot
import org.nowstart.lotto.application.port.outbound.LottoAutomationPort
import org.nowstart.lotto.application.port.outbound.LottoResult
import org.nowstart.lotto.application.port.outbound.LottoUser
import org.nowstart.lotto.application.port.outbound.PurchaseSubmission
import org.nowstart.lotto.application.port.outbound.PurchaseSubmission.Status
import org.nowstart.lotto.domain.calendar.LottoDrawCalendar
import org.nowstart.lotto.domain.exception.LedgerUnreadableException

class LottoPurchaseProcedureTest {

    private val port = mockk<LottoAutomationPort>()
    private val session = FakeSession()
    private val clock = MutableClock(MONDAY_NOON.toInstant())
    private val user = LottoUser("user1", "password", 5, "user1@nowstart.org", false)
    private val account = LottoAccountSnapshot("홍길동", "60,000원")
    private val registry = UncertainPurchaseRegistry(clock)
    private val procedure = LottoPurchaseProcedure(port, LottoDrawCalendar(clock), registry)

    @Test
    fun `이번 회차를 이미 5장 샀으면 결제 클릭 없이 기존 티켓을 안내한다 (2026-11-23 사례)`() {
        val sunday = lotto("2026-11-22", 1252, 5)
        every { port.readLedger(session) } returns listOf(sunday)
        every { port.captureTickets(session, listOf(sunday)) } returns listOf(CheckResult(sunday, byteArrayOf(1)))

        val outcome = procedure.purchase(session, user, account) { false }

        assertThat(outcome).isInstanceOfSatisfying(PurchaseOutcome.AlreadyPurchased::class.java) {
            assertThat(it.plan.alreadyPurchased).isEqualTo(5)
            assertThat(it.tickets).hasSize(1)
        }
        verify(exactly = 0) { port.buy(any(), any(), any(), any()) }
    }

    @Test
    fun `일부만 샀으면 모자란 수량만 산다`() {
        every { port.readLedger(session) } returns listOf(lotto("2026-11-22", 1252, 2))
        every { port.buy(session, user, 3, any()) } returns PurchaseSubmission(Status.NOT_CLICKED)

        assertThat(procedure.purchase(session, user, account) { false }).isInstanceOf(PurchaseOutcome.Aborted::class.java)
        verify { port.buy(session, user, 3, any()) }
    }

    @Test
    fun `원장이 빈 신규 계정과 다른 게임만 있는 계정은 설정 수량만큼 산다`() {
        val pension = LottoResult("2026-11-20", "290", "연금복권720+", "1조123456", "1", "미추첨", "1,000")
        every { port.readLedger(session) } returnsMany listOf(emptyList(), listOf(pension))
        every { port.buy(session, user, 5, any()) } returns PurchaseSubmission(Status.DRY_RUN)

        assertThat(procedure.purchase(session, user, account) { false }).isInstanceOf(PurchaseOutcome.DryRun::class.java)
        assertThat(procedure.purchase(session, user, account) { false }).isInstanceOf(PurchaseOutcome.DryRun::class.java)
        verify(exactly = 0) { port.awaitLedger(any(), any(), any(), any()) }
    }

    @Test
    fun `예치금이 부족하거나 원장에 계산보다 큰 회차가 있으면 구매하지 않는다`() {
        every { port.readLedger(session) } returnsMany listOf(emptyList(), listOf(lotto("2026-11-22", 1253, 1)))

        val poor = procedure.purchase(session, user, LottoAccountSnapshot("홍길동", "2,000원")) { false }
        val drift = procedure.purchase(session, user, account) { false }

        assertThat(poor).isInstanceOfSatisfying(PurchaseOutcome.Refused::class.java) { assertThat(it.reason).contains("예치금") }
        assertThat(drift).isInstanceOf(PurchaseOutcome.Refused::class.java)
        verify(exactly = 0) { port.buy(any(), any(), any(), any()) }
    }

    @Test
    fun `원장을 판독하지 못하면 예외를 그대로 올리고 구매하지 않는다`() {
        every { port.readLedger(session) } throws LedgerUnreadableException("no rows, no marker")

        assertThatThrownBy { procedure.purchase(session, user, account) { false } }
            .isInstanceOf(LedgerUnreadableException::class.java)
        verify(exactly = 0) { port.buy(any(), any(), any(), any()) }
    }

    @Test
    fun `확정 클릭 후 원장에 새 행이 보이면 구매 성공이다`() {
        val fresh = lotto("2026-11-23", 1252, 5)
        clickedWith(before = emptyList(), after = listOf(fresh))
        every { port.captureTickets(session, listOf(fresh)) } returns listOf(CheckResult(fresh, byteArrayOf(7)))

        val outcome = procedure.purchase(session, user, account) { false }

        assertThat(outcome).isInstanceOfSatisfying(PurchaseOutcome.Purchased::class.java) { assertThat(it.tickets).hasSize(1) }
        verify(exactly = 0) { port.readAccount(any()) }
    }

    @Test
    fun `같은 수량의 기존 행이 있어도 클릭 전에 없던 행만 새 구매로 본다`() {
        val morning = lotto("2026-11-23", 1252, 2)
        val fresh = LottoResult("2026-11-23", "1252", "로또6/45", "AUTO-2", "2", "미추첨", "2,000")
        val fourGames = user.copy(count = 4)
        every { port.readLedger(session) } returns listOf(morning)
        every { port.buy(session, fourGames, 2, any()) } returns PurchaseSubmission(Status.CLICKED)
        stubLedgerAfterClick(listOf(morning, fresh))
        every { port.captureTickets(session, listOf(fresh)) } returns listOf(CheckResult(fresh, null))

        assertThat(procedure.purchase(session, fourGames, account) { false }).isInstanceOf(PurchaseOutcome.Purchased::class.java)
    }

    @Test
    fun `원장 반영이 늦어도 예치금이 줄었으면 구매 성공이고 재구매는 막는다`() {
        clickedWith(before = emptyList(), after = null)
        every { port.readAccount(session) } returns LottoAccountSnapshot("홍길동", "55,000원")

        val outcome = procedure.purchase(session, user, account) { false }

        assertThat(outcome).isInstanceOfSatisfying(PurchaseOutcome.Purchased::class.java) { assertThat(it.note).contains("예치금") }
        assertThat(registry.blockedUntil(user.id, 1252)).isNotNull()
    }

    @Test
    fun `구매 흔적이 없고 클릭 후 거부 문구가 떴으면 거부이며 재구매는 막는다`() {
        clickedWith(before = emptyList(), after = null, dialogs = listOf("이번 회차 구매한도를 초과했습니다"))
        every { port.readAccount(session) } returns account

        assertThat(procedure.purchase(session, user, account) { false }).isInstanceOf(PurchaseOutcome.Rejected::class.java)
        assertThat(registry.blockedUntil(user.id, 1252)).isNotNull()
    }

    @Test
    fun `성공 안내에도 나오는 단어(잔액)만으로는 거부로 보지 않는다`() {
        clickedWith(before = emptyList(), after = null, dialogs = listOf("구매가 완료되었습니다. 잔액: 55,000원"))
        every { port.readAccount(session) } returns account

        assertThat(procedure.purchase(session, user, account) { false }).isInstanceOf(PurchaseOutcome.Uncertain::class.java)
    }

    @Test
    fun `증거가 없으면 판정 불가로 보고 원장 반영 대기 시간 동안 같은 회차 재구매를 막는다`() {
        clickedWith(before = emptyList(), after = null)
        every { port.readAccount(session) } returns account

        assertThat(procedure.purchase(session, user, account) { false }).isInstanceOf(PurchaseOutcome.Uncertain::class.java)
        assertThat(procedure.precheck(user)).isInstanceOf(PurchaseOutcome.Blocked::class.java)

        clock.advance(UncertainPurchaseRegistry.LEDGER_LAG)
        assertThat(procedure.precheck(user)).isNull()
    }

    @Test
    fun `확정 클릭 뒤 판정 중 예외가 나도 판정 불가로 보고 재구매를 막는다`() {
        every { port.readLedger(session) } returns emptyList()
        every { port.buy(session, user, 5, any()) } returns PurchaseSubmission(Status.CLICKED)
        every { port.awaitLedger(session, any(), any(), any()) } throws IllegalStateException("Target page has been closed")

        assertThat(procedure.purchase(session, user, account) { false }).isInstanceOf(PurchaseOutcome.Uncertain::class.java)
        assertThat(registry.blockedUntil(user.id, 1252)).isNotNull()
    }

    @Test
    fun `판매 시간이 아니면 원장도 읽지 않고 다음 판매 시각을 알려 준다`() {
        clock.set(ZonedDateTime.parse("2026-11-28T20:30:00+09:00[Asia/Seoul]").toInstant())

        val outcome = procedure.purchase(session, user, account) { false }

        assertThat(outcome).isEqualTo(
            PurchaseOutcome.NotOnSale(ZonedDateTime.parse("2026-11-29T06:00:00+09:00[Asia/Seoul]"), 1253),
        )
        verify(exactly = 0) { port.readLedger(any()) }
    }

    @Test
    fun `구매가 확인된 뒤 티켓 캡처가 실패해도 결과는 구매 성공이다`() {
        val fresh = lotto("2026-11-23", 1252, 5)
        clickedWith(before = emptyList(), after = listOf(fresh))
        every { port.captureTickets(session, any()) } throws IllegalStateException("modal timeout")

        val outcome = procedure.purchase(session, user, account) { false }

        assertThat(outcome).isInstanceOfSatisfying(PurchaseOutcome.Purchased::class.java) {
            assertThat(it.tickets.single().detailImage).isNull()
        }
    }

    private fun clickedWith(before: List<LottoResult>, after: List<LottoResult>?, dialogs: List<String> = emptyList()) {
        every { port.readLedger(session) } returns before
        every { port.buy(session, user, 5, any()) } returns PurchaseSubmission(Status.CLICKED, dialogs)
        if (after == null) {
            every { port.awaitLedger(session, any(), any(), any()) } returns null
        } else {
            stubLedgerAfterClick(after)
        }
    }

    private fun stubLedgerAfterClick(after: List<LottoResult>) {
        every { port.awaitLedger(session, any(), any(), any()) } answers {
            val condition = secondArg<(List<LottoResult>) -> Boolean>()
            if (condition(after)) after else null
        }
    }

    private companion object {
        /** 2026-11-23(월) 12:00 KST: 판매 회차 1252. */
        val MONDAY_NOON: ZonedDateTime = ZonedDateTime.parse("2026-11-23T12:00:00+09:00[Asia/Seoul]")

        fun lotto(date: String, round: Int, count: Int): LottoResult =
            LottoResult(date, "$round", "로또6/45", "AUTO-$date-$count", "$count", "미추첨", "%,d".format(count * 1000))
    }
}
