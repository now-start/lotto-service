package org.nowstart.lotto.application.service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort.LottoUser;
import org.nowstart.lotto.domain.type.TaskMode;

public interface LottoUserRunner {

    /**
     * @param abortSignal 호출자 타임아웃 등으로 작업을 중단시키기 위한 신호. 러너는 세션 확보/로그인/구매 등
     *                    안전 지점에서 이 값을 확인해, 특히 실거래(구매) 직전에는 반드시 확인 후 중단한다.
     */
    CompletableFuture<Boolean> runAsync(LottoUser user, TaskMode mode, AtomicBoolean abortSignal);
}
