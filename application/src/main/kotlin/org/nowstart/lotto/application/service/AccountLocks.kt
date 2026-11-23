package org.nowstart.lotto.application.service

import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Component

/**
 * 계정별 브라우저 사용 잠금. 같은 계정으로 두 번 로그인하면 사이트가 앞 세션을 끊어(구매 도중일 수 있음)
 * 작업 시도와 기동 초기화 모두 이 잠금을 잡은 뒤에만 브라우저를 연다.
 */
@Component
class AccountLocks {

    private val busy: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun tryAcquire(userId: String): Boolean = busy.add(userId)

    fun release(userId: String) {
        busy.remove(userId)
    }

    fun isIdle(): Boolean = busy.isEmpty()
}
