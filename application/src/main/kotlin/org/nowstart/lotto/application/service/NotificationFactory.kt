package org.nowstart.lotto.application.service

import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.nowstart.lotto.application.port.outbound.CheckResult
import org.nowstart.lotto.application.port.outbound.AccountSnapshot
import org.nowstart.lotto.domain.ledger.TicketResult
import org.nowstart.lotto.application.port.outbound.UserAccount
import org.nowstart.lotto.application.port.outbound.NotificationMessage
import org.nowstart.lotto.domain.calendar.DrawCalendar
import org.nowstart.lotto.domain.type.MessageType
import org.nowstart.lotto.domain.type.TaskMode

class NotificationFactory {

    fun createCheckSuccessMessage(
        user: UserAccount,
        accountSnapshot: AccountSnapshot,
        results: List<CheckResult>,
    ): NotificationMessage? {
        val latest = results.maxByOrNull { it.result.round.toInt() } ?: return null
        return NotificationMessage(
            "[${user.id}] ${summary(latest.result)}",
            createResultMessageText(accountSnapshot, latest.result),
            latest.detailImage,
            user.email,
        )
    }

    fun createFailureMessage(user: UserAccount, mode: TaskMode, exception: Exception): NotificationMessage {
        val failureMessage = """
            |[사용자: ${user.id}]
            |작업 실행 중 오류가 발생했습니다.
            |
            |오류 유형: ${exception.javaClass.simpleName}
            |오류 메시지: ${exception.message}
            |
            |자세한 내용은 서버 로그를 확인해 주세요.
        """.trimMargin()
        return NotificationMessage("[${user.id}] ${mode.failureSubject}", failureMessage, null, user.email)
    }

    /** 구매 결과(성공·이미 구매·보류·거부·판정 불가 등)에 맞는 통지를 만든다. dry-run과 중단은 통지하지 않는다. */
    fun createPurchaseOutcomeMessage(
        user: UserAccount,
        accountSnapshot: AccountSnapshot?,
        outcome: PurchaseOutcome,
    ): NotificationMessage? = when (outcome) {
        is PurchaseOutcome.Purchased -> purchasedMessage(user, accountSnapshot, outcome)
        is PurchaseOutcome.AlreadyPurchased -> alreadyPurchasedMessage(user, accountSnapshot, outcome)
        is PurchaseOutcome.NotOnSale -> notice(
            user, "⏸️로또 판매 시간이 아닙니다",
            """
                |지금은 자동 구매를 하지 않는 시간입니다.
                |다음 구매 가능 시각: ${format(outcome.nextPurchasableAt)} (${outcome.nextRound}회차)
                |
                |판매 시간: 매일 06:00~24:00, 추첨일(토)은 20:00 마감
                |(마감 직전 10분인 토요일 19:50 이후에는 자동 구매를 시도하지 않습니다)
            """.trimMargin(),
        )
        is PurchaseOutcome.Blocked -> notice(
            user, "⏸️로또 구매 보류 — 수동 구매 금지",
            """
                |${outcome.round}회차 직전 구매 시도의 결과가 아직 원장에 반영되지 않았을 수 있어, 이중 구매를 막기 위해 구매하지 않았습니다.
                |${format(outcome.until)} 이후에 다시 실행하세요. 그 전에 수동으로 구매하지 마세요.
            """.trimMargin(),
        )
        is PurchaseOutcome.Refused -> notice(user, TaskMode.PURCHASE.failureSubject, "구매하지 않았습니다.\n사유: ${outcome.reason}")
        is PurchaseOutcome.Rejected -> notice(
            user, TaskMode.PURCHASE.failureSubject,
            """
                |${outcome.plan.round}회차 ${outcome.plan.toBuy}장 구매를 사이트가 거부했습니다(원장·예치금 변화 없음).
                |사이트 메시지: ${outcome.siteMessages.joinToString(" / ")}
            """.trimMargin(),
        )
        is PurchaseOutcome.Uncertain -> notice(
            user, "❓로또 구매 확인 필요 — 수동 구매 금지",
            """
                |${outcome.plan.round}회차 ${outcome.plan.toBuy}장 최종 확정 클릭은 했지만, 원장과 예치금으로 구매를 확인하지 못했습니다.
                |이중 구매 위험이 있으니 수동으로 다시 구매하지 마세요.
                |${format(outcome.recheckAfter)} 이후 결과 확인을 실행하거나 동행복권 구매내역을 확인해 주세요.
            """.trimMargin(),
        )
        is PurchaseOutcome.DryRun -> null
        is PurchaseOutcome.Aborted -> null
    }

    fun createInitializationMessage(user: UserAccount, accountSnapshot: AccountSnapshot): NotificationMessage =
        NotificationMessage("⏳[${user.id}] Lotto Init Test⏳", accountSnapshotText(accountSnapshot), null, user.email)

    private fun purchasedMessage(
        user: UserAccount,
        accountSnapshot: AccountSnapshot?,
        purchased: PurchaseOutcome.Purchased,
    ): NotificationMessage {
        val note = purchased.note?.let { "\n\n$it" }.orEmpty()
        val withTicket = accountSnapshot?.let { createCheckSuccessMessage(user, it, purchased.tickets) }
        if (withTicket != null) {
            return NotificationMessage(withTicket.subject, withTicket.text + note, withTicket.inlineImage, withTicket.to)
        }
        return notice(
            user, "✅로또6/45 ${purchased.plan.round}회차 구매 완료",
            "${accountSnapshotText(accountSnapshot)}\n\n구매 수량: ${purchased.plan.toBuy}장$note",
        )
    }

    private fun alreadyPurchasedMessage(
        user: UserAccount,
        accountSnapshot: AccountSnapshot?,
        already: PurchaseOutcome.AlreadyPurchased,
    ): NotificationMessage {
        val rows = already.tickets.joinToString("\n") { "- ${it.result.date} ${it.result.count}장 ${it.result.result}" }
        val image = already.tickets.firstNotNullOfOrNull { it.detailImage }
        val text = """
            |${accountSnapshotText(accountSnapshot)}
            |
            |이번 회차는 이미 ${already.plan.alreadyPurchased}장 구매되어 추가로 구매하지 않았습니다.
            |$rows
        """.trimMargin()
        return NotificationMessage("[${user.id}] ✅로또6/45 ${already.plan.round}회차 이미 구매됨", text, image, user.email)
    }

    private fun notice(user: UserAccount, subject: String, text: String): NotificationMessage =
        NotificationMessage("[${user.id}] $subject", text, null, user.email)

    private fun createResultMessageText(accountSnapshot: AccountSnapshot, result: TicketResult): String = """
        |${accountSnapshotText(accountSnapshot)}
        |
        |일자: ${result.date}
        |회차: ${result.round}
        |게임: ${result.name}
        |번호: ${result.number}
        |수량: ${result.count}
        |결과: ${result.result}
        |금액: ${result.price}
        |
    """.trimMargin()

    private fun summary(result: TicketResult): String {
        val emoji = when (MessageType.of(result.result)) {
            MessageType.WAIT -> "⏳"
            MessageType.WIN -> "🎉"
            MessageType.NO_WIN -> "☠️"
            MessageType.UNDEFINED -> ""
        }
        return "$emoji${result.name} ${result.round}회차$emoji"
    }

    private val TaskMode.failureSubject: String
        get() = when (this) {
            TaskMode.CHECK -> "⚠️로또 확인 실패⚠️"
            TaskMode.PURCHASE -> "⚠️로또 구매 실패⚠️"
        }

    private fun accountSnapshotText(accountSnapshot: AccountSnapshot?): String =
        accountSnapshot?.let { "${it.name}의 💰예치금 : ${it.deposit}" } ?: "(계정 정보 없음)"

    private companion object {
        val DISPLAY_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        fun format(time: Instant): String = DISPLAY_TIME.format(time.atZone(DrawCalendar.ZONE))

        fun format(time: ZonedDateTime): String = DISPLAY_TIME.format(time)
    }
}
