package org.nowstart.lotto.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 확정 클릭은 했지만 구매 여부를 확인하지 못한 (계정, 회차)를 기억한다.
 * 원장 반영이 늦을 수 있으므로, 마지막 클릭 가능 시각부터 {@link #LEDGER_LAG} 동안은 원장이 비어 보여도 다시 사지 않는다.
 * 작업보다 오래 유지되는 프로세스 메모리 상태다(재시작 시 사라짐).
 */
@Slf4j
@Component
public class UncertainPurchaseRegistry {

    /** 확정 클릭 후 원장에 반영되기까지 기다려 줄 최대 시간. */
    static final Duration LEDGER_LAG = Duration.ofMinutes(10);

    private final Map<Key, Instant> lastPossibleClickAt = new ConcurrentHashMap<>();
    private final Clock clock;

    public UncertainPurchaseRegistry(Clock clock) {
        this.clock = clock;
    }

    public Instant record(String userId, int round) {
        Instant now = clock.instant();
        lastPossibleClickAt.put(new Key(userId, round), now);
        Instant until = now.plus(LEDGER_LAG);
        log.warn("[Uncertain][{}] round={} - 원장 판정 대기, {}까지 재구매 보류", userId, round, until);
        return until;
    }

    /** 아직 원장을 믿을 수 없으면 보류 해제 시각을 돌려준다. */
    public Optional<Instant> blockedUntil(String userId, int round) {
        Key key = new Key(userId, round);
        Instant clickedAt = lastPossibleClickAt.get(key);
        if (clickedAt == null) {
            return Optional.empty();
        }
        Instant until = clickedAt.plus(LEDGER_LAG);
        if (!clock.instant().isBefore(until)) {
            lastPossibleClickAt.remove(key, clickedAt);
            return Optional.empty();
        }
        return Optional.of(until);
    }

    private record Key(String userId, int round) {
    }
}
