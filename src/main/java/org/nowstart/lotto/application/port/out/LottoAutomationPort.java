package org.nowstart.lotto.application.port.out;

import java.util.List;
import org.nowstart.lotto.domain.model.LottoAccountSnapshot;
import org.nowstart.lotto.domain.model.LottoResult;
import org.nowstart.lotto.domain.model.LottoUser;

public interface LottoAutomationPort {

    record CheckResult(LottoResult result, byte[] detailImage) {

            public CheckResult(LottoResult result, byte[] detailImage) {
                this.result = result;
                this.detailImage = detailImage == null ? null : detailImage.clone();
            }

            @Override
            public byte[] detailImage() {
                return detailImage == null ? null : detailImage.clone();
            }
    }

    LottoAutomationSession openSession();

    LottoAccountSnapshot login(LottoAutomationSession session, LottoUser user);

    void buy(LottoAutomationSession session, LottoUser user);

    List<CheckResult> check(LottoAutomationSession session);
}
