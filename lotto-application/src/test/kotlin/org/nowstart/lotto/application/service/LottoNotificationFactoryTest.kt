package org.nowstart.lotto.application.service

import java.time.Instant
import java.time.ZonedDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.nowstart.lotto.application.port.outbound.CheckResult
import org.nowstart.lotto.application.port.outbound.LottoAccountSnapshot
import org.nowstart.lotto.application.port.outbound.LottoResult
import org.nowstart.lotto.application.port.outbound.LottoUser
import org.nowstart.lotto.domain.ledger.PurchasePlan

class LottoNotificationFactoryTest {

    private val factory = LottoNotificationFactory()
    private val user = LottoUser("user1", "password", 5, "user1@nowstart.org", false)
    private val account = LottoAccountSnapshot("홍길동", "5000원")

    @Test
    fun `확인 성공 알림에 구매 번호와 예치금을 포함한다`() {
        val result = CheckResult(LottoResult("2026-04-14", "1234", "로또", "1,2,3,4,5,6", "1", "미추첨", "1000원"), byteArrayOf(1))

        val message = factory.createCheckSuccessMessage(user, account, listOf(result))

        assertThat(message?.text).contains("번호: 1,2,3,4,5,6").contains("홍길동의 💰예치금 : 5000원")
        assertThat(message?.inlineImage).containsExactly(1)
    }

    @Test
    fun `여러 결과가 있으면 가장 최신 회차를 알림 대상으로 선택한다`() {
        val older = CheckResult(LottoResult("2026-04-13", "1233", "로또", "1,2,3,4,5,6", "1", "미추첨", "1000원"), byteArrayOf(1))
        val latest = CheckResult(LottoResult("2026-04-12", "1234", "로또", "7,8,9,10,11,12", "1", "미추첨", "1000원"), byteArrayOf(2))

        val message = factory.createCheckSuccessMessage(user, account, listOf(older, latest))

        assertThat(message?.subject).contains("1234회차")
        assertThat(message?.text).contains("회차: 1234").contains("번호: 7,8,9,10,11,12")
        assertThat(message?.inlineImage).containsExactly(2)
    }

    @Test
    fun `이미 구매했으면 회차와 기존 티켓 이미지를 담아 알린다`() {
        val ticket = LottoResult("2026-11-22", "1252", "로또6/45", "A", "5", "미추첨", "5,000")
        val outcome = PurchaseOutcome.AlreadyPurchased(PurchasePlan.of(1252, 5, 5), listOf(CheckResult(ticket, byteArrayOf(1))))

        val message = factory.createPurchaseOutcomeMessage(user, account, outcome)

        assertThat(message?.subject).isEqualTo("[user1] ✅로또6/45 1252회차 이미 구매됨")
        assertThat(message?.text).contains("이미 5장 구매되어 추가로 구매하지 않았습니다")
        assertThat(message?.hasInlineImage).isTrue()
    }

    @Test
    fun `판정 불가는 구매 실패가 아니라 수동 구매 금지 안내로 알린다`() {
        val outcome = PurchaseOutcome.Uncertain(PurchasePlan.of(1252, 5, 0), Instant.parse("2026-11-23T02:40:00Z"))

        val message = factory.createPurchaseOutcomeMessage(user, account, outcome)

        assertThat(message?.subject).contains("수동 구매 금지").doesNotContain("실패")
        assertThat(message?.text).contains("2026-11-23 11:40").contains("다시 구매하지 마세요")
    }

    @Test
    fun `판매 시간이 아니면 다음 구매 가능 시각과 회차를 알린다`() {
        val outcome = PurchaseOutcome.NotOnSale(ZonedDateTime.parse("2026-11-29T06:00:00+09:00[Asia/Seoul]"), 1253)

        assertThat(factory.createPurchaseOutcomeMessage(user, null, outcome)?.text).contains("2026-11-29 06:00 (1253회차)")
    }

    @Test
    fun `티켓 이미지 없이 예치금으로 확인한 구매도 성공으로 알린다`() {
        val outcome = PurchaseOutcome.Purchased(PurchasePlan.of(1252, 5, 0), emptyList(), "예치금 차감으로 확인")

        val message = factory.createPurchaseOutcomeMessage(user, account, outcome)

        assertThat(message?.subject).isEqualTo("[user1] ✅로또6/45 1252회차 구매 완료")
        assertThat(message?.text).contains("구매 수량: 5장").contains("예치금 차감으로 확인")
    }

    @Test
    fun `dry-run과 중단은 이 경로로 알리지 않는다`() {
        assertThat(factory.createPurchaseOutcomeMessage(user, account, PurchaseOutcome.DryRun(PurchasePlan.of(1252, 5, 0)))).isNull()
        assertThat(factory.createPurchaseOutcomeMessage(user, account, PurchaseOutcome.Aborted("최종 확정 전"))).isNull()
    }
}
