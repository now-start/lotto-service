package org.nowstart.lotto.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort.LottoUser;

public interface LottoAutomationPort {

    LottoAutomationSession openSession();

    LottoAccountSnapshot login(LottoAutomationSession session, LottoUser user);

    /**
     * @param abortRequested 최종 확정(실결제) 직전 확인용 중단 신호. true면 확정 클릭을 하지 않고
     *                       Optional.empty()를 반환한다(구매 미수행).
     * @return 구매 영수증. 최종 확정 전에 중단되면 Optional.empty().
     */
    Optional<PurchaseReceipt> buy(LottoAutomationSession session, LottoUser user, BooleanSupplier abortRequested);

    List<CheckResult> check(LottoAutomationSession session);

    CheckResult check(LottoAutomationSession session, PurchaseReceipt purchaseReceipt);

    record LottoAccountSnapshot(
            String name,
            String deposit
    ) {
    }

    record LottoResult(
            String date,
            String round,
            String name,
            String number,
            String count,
            String result,
            String price
    ) {
    }

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

    record PurchaseReceipt(int count, LocalDate purchaseDate) {

        public PurchaseReceipt {
            if (count <= 0) {
                throw new IllegalArgumentException("count must be positive");
            }
            Objects.requireNonNull(purchaseDate, "purchaseDate must not be null");
        }
    }
}
