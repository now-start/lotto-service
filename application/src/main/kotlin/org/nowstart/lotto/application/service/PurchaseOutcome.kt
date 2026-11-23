package org.nowstart.lotto.application.service

import java.time.Instant
import java.time.ZonedDateTime
import org.nowstart.lotto.application.port.outbound.CheckResult
import org.nowstart.lotto.domain.ledger.PurchasePlan

/** 한 번의 구매 시도 결과. 사용자 통지와 성공 집계의 기준이다. */
sealed interface PurchaseOutcome {

    /** 구매가 확인됨(원장 새 행 또는 예치금 차감). 티켓 이미지가 없을 수 있다. */
    data class Purchased(val plan: PurchasePlan, val tickets: List<CheckResult>, val note: String? = null) : PurchaseOutcome

    /** 이번 회차는 이미 목표 수량을 샀다. 결제 클릭을 하지 않았다. */
    data class AlreadyPurchased(val plan: PurchasePlan, val tickets: List<CheckResult>) : PurchaseOutcome

    /** dry-run이라 최종 확정 직전에 멈췄다. */
    data class DryRun(val plan: PurchasePlan) : PurchaseOutcome

    /** 판매 시간이 아니다. */
    data class NotOnSale(val nextPurchasableAt: ZonedDateTime, val nextRound: Int) : PurchaseOutcome

    /** 직전 시도의 원장 반영을 기다리는 중이라 구매를 보류했다. */
    data class Blocked(val round: Int, val until: Instant) : PurchaseOutcome

    /** 구매 전에 스스로 거부했다(예치금 부족, 회차 계산 불일치 등). */
    data class Refused(val reason: String) : PurchaseOutcome

    /** 확정 클릭 후 사이트가 거부 문구를 띄웠고 구매 흔적이 없다. */
    data class Rejected(val plan: PurchasePlan, val siteMessages: List<String>) : PurchaseOutcome

    /** 확정 클릭은 했지만 구매 여부를 확인하지 못했다. 수동 재구매 금지. */
    data class Uncertain(val plan: PurchasePlan, val recheckAfter: Instant) : PurchaseOutcome

    /** 호출자 타임아웃 등으로 최종 확정 전에 중단했다. */
    data class Aborted(val phase: String) : PurchaseOutcome

    val isSuccess: Boolean
        get() = this is Purchased || this is AlreadyPurchased || this is DryRun

    /** 진단 자료(트레이스)를 남겨야 하는 결과인지. */
    val needsDiagnostics: Boolean
        get() = this is Uncertain || this is Rejected || this is Refused
}
