package org.nowstart.lotto.application.service

/**
 * 애플리케이션 계층이 쓰는 실행 설정. 설정 파일 구조(LottoProperties)에 의존하지 않도록 루트 앱이 만들어 넘긴다.
 *
 * @property userTaskTimeoutMs 사용자 1인 작업을 호출자가 기다리는 최대 시간
 */
data class ExecutionSettings(val userTaskTimeoutMs: Long)
