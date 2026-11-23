package org.nowstart.lotto.domain.type

enum class ExecutionStatus {
    SUCCESS,
    PARTIAL_FAILURE,
    FAILURE;

    companion object {
        fun fromCounts(totalUsers: Int, failedUsers: Int): ExecutionStatus = when {
            failedUsers <= 0 -> SUCCESS
            failedUsers >= totalUsers -> FAILURE
            else -> PARTIAL_FAILURE
        }
    }
}

enum class MessageType(val text: String) {
    WAIT("미추첨"),
    WIN("당첨"),
    NO_WIN("낙첨"),
    UNDEFINED("");

    companion object {
        fun of(text: String?): MessageType = entries.firstOrNull { it.text == text } ?: UNDEFINED
    }
}

enum class StepType {
    LOGIN,
    PURCHASE,
    CHECK
}

enum class TaskMode {
    CHECK,
    PURCHASE
}

enum class TriggerType {
    SCHEDULE,
    MANUAL
}
