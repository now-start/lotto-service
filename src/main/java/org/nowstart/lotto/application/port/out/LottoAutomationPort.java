package org.nowstart.lotto.application.port.out;

import java.util.List;
import org.nowstart.lotto.domain.model.LottoAccountSnapshot;
import org.nowstart.lotto.domain.model.LottoResult;
import org.nowstart.lotto.domain.model.LottoUser;

public interface LottoAutomationPort {

    LottoAutomationSession openSession();

    LottoAccountSnapshot login(LottoAutomationSession session, LottoUser user);

    void buy(LottoAutomationSession session, LottoUser user);

    List<LottoResult> check(LottoAutomationSession session);
}
