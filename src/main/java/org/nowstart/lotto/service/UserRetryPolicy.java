package org.nowstart.lotto.service;

import org.nowstart.lotto.data.entity.UserEntity;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.context.RetryContextSupport;

public class UserRetryPolicy implements RetryPolicy {

    @Override
    public boolean canRetry(RetryContext context) {
        UserEntity user = TaskProcessor.CURRENT_USER.get();
        if (user != null && user.getMaxRetries() != null) {
            return context.getRetryCount() < user.getMaxRetries();
        }
        return context.getRetryCount() < 3;
    }

    @Override
    public RetryContext open(RetryContext parent) {
        return new RetryContextSupport(parent);
    }

    @Override
    public void close(RetryContext context) {
    }

    @Override
    public void registerThrowable(RetryContext context, Throwable throwable) {
        ((RetryContextSupport) context).registerThrowable(throwable);
    }
}
