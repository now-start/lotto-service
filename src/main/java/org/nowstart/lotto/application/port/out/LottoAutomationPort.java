package org.nowstart.lotto.application.port.out;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort.LottoUser;

public interface LottoAutomationPort {

    LottoAutomationSession openSession();

    /** 로그인하고 이름·예치금을 읽는다. 진단 트레이스는 로그인 이후부터 기록한다. */
    LottoAccountSnapshot login(LottoAutomationSession session, LottoUser user);

    /** 이미 로그인된 세션에서 이름·예치금을 다시 읽는다(구매 후 예치금 차감 확인용). */
    LottoAccountSnapshot readAccount(LottoAutomationSession session);

    /**
     * 구매내역(원장)을 읽는다. 행도 빈 결과 표시도 없거나 행을 해석하지 못하면
     * {@link org.nowstart.lotto.domain.exception.LedgerUnreadableException}을 던진다(fail-closed).
     */
    List<LottoResult> readLedger(LottoAutomationSession session);

    /**
     * 조건을 만족할 때까지 원장을 다시 읽는다. 시간 안에 만족하지 않거나 중단 요청이 오면 빈 값을 돌려준다.
     * 중간의 판독 실패는 다음 읽기로 넘어간다.
     */
    Optional<List<LottoResult>> awaitLedger(
            LottoAutomationSession session,
            Predicate<List<LottoResult>> condition,
            Duration timeout,
            BooleanSupplier abortRequested
    );

    /** 원장 행들의 티켓 이미지를 캡처한다. 캡처에 실패한 행은 이미지 없이 돌려준다. */
    List<CheckResult> captureTickets(LottoAutomationSession session, List<LottoResult> rows);

    /**
     * 자동 번호로 {@code count}장을 구매 신청한다. 멱등하지 않으므로 절대 재시도하지 않는다.
     *
     * @param abortRequested 최종 확정 클릭 직전에 확인하는 중단 신호
     */
    PurchaseSubmission buy(LottoAutomationSession session, LottoUser user, int count, BooleanSupplier abortRequested);

    /** 결과 확인용: 원장의 모든 행과 티켓 이미지를 가져온다. */
    List<CheckResult> check(LottoAutomationSession session);

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

        /** 원장 행을 식별하는 키(클릭 전후 비교용). */
        public String key() {
            return String.join("|",
                    normalize(date), normalize(round), normalize(name), normalize(number),
                    normalize(count), normalize(result), normalize(price));
        }

        private static String normalize(String text) {
            return text == null ? "" : text.trim().replaceAll("\\s+", " ");
        }
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

    /**
     * 구매 신청 결과. {@link Status#CLICKED}는 확정 클릭을 시도했다는 뜻일 뿐이며,
     * 실제 구매 여부는 원장·예치금으로 판정해야 한다.
     *
     * @param dialogMessages 구매 화면에서 뜬 브라우저 대화상자 문구(사이트의 거부 사유 판단용)
     */
    record PurchaseSubmission(Status status, List<String> dialogMessages) {

        public PurchaseSubmission {
            Objects.requireNonNull(status, "status must not be null");
            dialogMessages = dialogMessages == null ? List.of() : List.copyOf(dialogMessages);
        }

        public static PurchaseSubmission of(Status status) {
            return new PurchaseSubmission(status, List.of());
        }

        public enum Status {
            /** 최종 확정 전에 중단됨(구매 미수행이 확실함). */
            NOT_CLICKED,
            /** dry-run이라 최종 확정 직전에 멈춤(구매 미수행). */
            DRY_RUN,
            /** 최종 확정 클릭을 시도함(구매됐을 수 있음). */
            CLICKED
        }
    }
}
