package org.nowstart.lotto.application.service;

import java.util.List;
import org.nowstart.lotto.domain.model.LottoAccountSnapshot;
import org.nowstart.lotto.domain.model.LottoResult;

record LottoUserExecutionContext(
        LottoAccountSnapshot accountSnapshot,
        List<LottoResult> results
) {
}
