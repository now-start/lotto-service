package org.nowstart.lotto.adapter.outbound.browser

import com.microsoft.playwright.Page
import com.microsoft.playwright.PlaywrightException
import java.lang.reflect.Method
import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.nowstart.lotto.application.port.outbound.LottoUser
import org.springframework.resilience.annotation.Retryable

class PlaywrightRetryAnnotationTest {

    @Test
    fun `조회성 executor(로그인, 결과 확인)는 일시 오류에 한해 재시도를 선언한다`() {
        assertRetryable(PlaywrightLoginExecutor::class.java.getMethod("login", Page::class.java, LottoUser::class.java))
        assertRetryable(PlaywrightResultExecutor::class.java.getMethod("check", Page::class.java))
    }

    @Test
    fun `구매와 구매 판정용 원장 읽기는 재시도를 선언하지 않는다`() {
        val purchase = PlaywrightPurchaseExecutor::class.java
        val result = PlaywrightResultExecutor::class.java

        listOf(
            purchase.getMethod("buy", Page::class.java, LottoUser::class.java, Int::class.javaPrimitiveType, Function0::class.java),
            result.getMethod("readLedger", Page::class.java),
            result.getMethod("awaitLedger", Page::class.java, Function1::class.java, Duration::class.java, Function0::class.java),
            result.getMethod("captureTickets", Page::class.java, List::class.java),
        ).forEach { assertThat(it.getAnnotation(Retryable::class.java)).`as`(it.name).isNull() }
    }

    private fun assertRetryable(method: Method) {
        val retryable = method.getAnnotation(Retryable::class.java)

        assertThat(retryable).isNotNull()
        assertThat(retryable.includes.map { it.java }).containsExactly(PlaywrightException::class.java)
        assertThat(retryable.maxRetriesString).isEqualTo("\${lotto.max-retries:3}")
        assertThat(retryable.delayString).isEqualTo("\${lotto.retry-delay-ms:2000}")
    }
}
