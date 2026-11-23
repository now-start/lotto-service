package org.nowstart.lotto.application.service;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.CheckResult;
import org.nowstart.lotto.domain.ledger.PurchasePlan;

/** 한 번의 구매 시도 결과. 사용자 통지와 성공 집계의 기준이다. */
public sealed interface PurchaseOutcome {

    /** 구매가 확인됨(원장 새 행 또는 예치금 차감). 티켓 이미지가 없을 수 있다. */
    record Purchased(PurchasePlan plan, List<CheckResult> tickets, String note) implements PurchaseOutcome {
        public Purchased {
            tickets = List.copyOf(tickets);
        }
    }

    /** 이번 회차는 이미 목표 수량을 샀다. 결제 클릭을 하지 않았다. */
    record AlreadyPurchased(PurchasePlan plan, List<CheckResult> tickets) implements PurchaseOutcome {
        public AlreadyPurchased {
            tickets = List.copyOf(tickets);
        }
    }

    /** dry-run이라 최종 확정 직전에 멈췄다. */
    record DryRun(PurchasePlan plan) implements PurchaseOutcome {
    }

    /** 판매 시간이 아니다. */
    record NotOnSale(ZonedDateTime nextPurchasableAt, int nextRound) implements PurchaseOutcome {
    }

    /** 직전 시도의 원장 반영을 기다리는 중이라 구매를 보류했다. */
    record Blocked(int round, Instant until) implements PurchaseOutcome {
    }

    /** 구매 전에 스스로 거부했다(예치금 부족, 회차 계산 불일치 등). */
    record Refused(String reason) implements PurchaseOutcome {
    }

    /** 확정 클릭 후 사이트가 거부 문구를 띄웠고 구매 흔적이 없다. */
    record Rejected(PurchasePlan plan, List<String> siteMessages) implements PurchaseOutcome {
        public Rejected {
            siteMessages = List.copyOf(siteMessages);
        }
    }

    /** 확정 클릭은 했지만 구매 여부를 확인하지 못했다. 수동 재구매 금지. */
    record Uncertain(PurchasePlan plan, Instant recheckAfter) implements PurchaseOutcome {
    }

    /** 호출자 타임아웃 등으로 최종 확정 전에 중단했다. */
    record Aborted(String phase) implements PurchaseOutcome {
    }

    default boolean isSuccess() {
        return this instanceof Purchased || this instanceof AlreadyPurchased || this instanceof DryRun;
    }

    /** 진단 자료(트레이스)를 남겨야 하는 결과인지. */
    default boolean needsDiagnostics() {
        return this instanceof Uncertain || this instanceof Rejected || this instanceof Refused;
    }
}
