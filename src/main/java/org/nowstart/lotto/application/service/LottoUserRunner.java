package org.nowstart.lotto.application.service;

import java.util.concurrent.CompletableFuture;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort.LottoUser;
import org.nowstart.lotto.domain.type.TaskMode;

public interface LottoUserRunner {

    CompletableFuture<Boolean> runAsync(LottoUser user, TaskMode mode);
}
