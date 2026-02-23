package org.nowstart.lotto.data.type;

public enum ExecutionStatus {
    SUCCESS,
    PARTIAL_FAILURE,
    FAILURE;

    public static ExecutionStatus fromCounts(int totalUsers, int failedUsers) {
        if (failedUsers <= 0) {
            return SUCCESS;
        }

        if (failedUsers >= totalUsers) {
            return FAILURE;
        }

        return PARTIAL_FAILURE;
    }
}
