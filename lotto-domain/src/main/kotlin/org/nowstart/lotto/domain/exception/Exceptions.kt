package org.nowstart.lotto.domain.exception

import org.nowstart.lotto.domain.type.StepType

/** 자동화 단계(로그인·구매·확인) 실패. */
class LottoAutomationException(
    val stepType: StepType,
    val userId: String,
    cause: Throwable,
) : RuntimeException("$stepType step failed for user $userId", cause)

/** 다시 보내도 소용없는 알림 발송 실패. */
class NotificationSendException(message: String, cause: Throwable?) : RuntimeException(message, cause)

/**
 * 구매내역(원장)을 확실하게 판독하지 못했을 때 던진다.
 * 구매 여부 판단은 이 예외가 나면 진행하지 않는다(fail-closed).
 */
class LedgerUnreadableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
