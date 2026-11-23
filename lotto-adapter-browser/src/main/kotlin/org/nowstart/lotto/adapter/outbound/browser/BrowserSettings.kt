package org.nowstart.lotto.adapter.outbound.browser

/**
 * 브라우저 어댑터 설정. 설정 파일 구조(LottoProperties)에 의존하지 않도록 루트 앱이 만들어 넘긴다.
 *
 * @property maxConcurrentSessions 동시에 띄우는 브라우저 세션 상한
 * @property sessionPermitTimeoutMs 브라우저 슬롯을 기다리는 최대 시간
 * @property dryRun true면 최종 결제 클릭 직전에 멈춘다
 */
data class BrowserSettings(
    val maxConcurrentSessions: Int,
    val sessionPermitTimeoutMs: Long,
    val dryRun: Boolean,
)
