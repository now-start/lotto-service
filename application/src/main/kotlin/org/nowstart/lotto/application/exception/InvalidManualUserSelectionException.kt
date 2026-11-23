package org.nowstart.lotto.application.exception

/** 수동 실행 요청에 설정에 없는 사용자 id가 포함됨. */
class InvalidManualUserSelectionException(
    invalidUserIds: List<String>,
    availableUserIds: List<String>,
) : RuntimeException("유효하지 않은 userId가 포함되어 있습니다.") {

    val invalidUserIds: List<String> = invalidUserIds.toList()
    val availableUserIds: List<String> = availableUserIds.toList()
}
