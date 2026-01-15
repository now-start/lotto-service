package org.nowstart.lotto.service;

import org.nowstart.lotto.data.entity.UserEntity;
import org.springframework.batch.core.step.skip.SkipPolicy;

public class UserSkipPolicy implements SkipPolicy {
    @Override
    public boolean shouldSkip(Throwable t, long skipCount) {
        UserEntity user = TaskProcessor.CURRENT_USER.get();
        if (user != null && user.getSkipLimit() != null) {
            return skipCount <= user.getSkipLimit();
        }
        return skipCount <= 100;
    }
}
