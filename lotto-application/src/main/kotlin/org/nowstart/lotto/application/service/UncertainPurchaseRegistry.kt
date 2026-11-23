package org.nowstart.lotto.application.service

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

private val log = LoggerFactory.getLogger(UncertainPurchaseRegistry::class.java)

/**
 * 확정 클릭은 했지만 원장으로 구매를 확인하지 못한 (계정, 회차)를 기억한다.
 * 원장 반영이 늦을 수 있으므로, 마지막 클릭 가능 시각부터 [LEDGER_LAG] 동안은 원장이 비어 보여도 다시 사지 않는다.
 * 작업보다 오래 유지되는 프로세스 메모리 상태다(재시작 시 사라짐).
 */
@Component
class UncertainPurchaseRegistry(private val clock: Clock) {

    private val lastPossibleClickAt = ConcurrentHashMap<Key, Instant>()

    fun record(userId: String, round: Int): Instant {
        val now = clock.instant()
        lastPossibleClickAt[Key(userId, round)] = now
        val until = now.plus(LEDGER_LAG)
        log.warn("[Uncertain][{}] round={} - 원장 판정 대기, {}까지 재구매 보류", userId, round, until)
        return until
    }

    /** 아직 원장을 믿을 수 없으면 보류 해제 시각을, 아니면 null을 돌려준다. */
    fun blockedUntil(userId: String, round: Int): Instant? {
        val key = Key(userId, round)
        val clickedAt = lastPossibleClickAt[key] ?: return null
        val until = clickedAt.plus(LEDGER_LAG)
        if (!clock.instant().isBefore(until)) {
            lastPossibleClickAt.remove(key, clickedAt)
            return null
        }
        return until
    }

    private data class Key(val userId: String, val round: Int)

    companion object {
        /** 확정 클릭 후 원장에 반영되기까지 기다려 줄 최대 시간. */
        val LEDGER_LAG: Duration = Duration.ofMinutes(10)
    }
}
