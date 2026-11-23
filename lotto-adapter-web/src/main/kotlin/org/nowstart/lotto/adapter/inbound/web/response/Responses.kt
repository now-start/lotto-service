package org.nowstart.lotto.adapter.inbound.web.response

import org.nowstart.lotto.application.port.inbound.ExecutionView
import org.nowstart.lotto.application.port.inbound.UserJobView

/** 202: 새 실행을 만들었다. alreadyActive는 이미 진행 중이라 새로 만들지 않은 계정 작업. */
data class StartResponse(val execution: ExecutionView, val alreadyActive: List<UserJobView>)

/** 409: 요청한 계정이 모두 이미 진행 중이다. 기존 실행 id로 상태를 조회하면 된다. */
data class ConflictResponse(val message: String, val alreadyActive: List<UserJobView>)

data class ManualExecutionErrorResponse(
    val message: String,
    val invalidUserIds: List<String>,
    val availableUserIds: List<String>,
)
