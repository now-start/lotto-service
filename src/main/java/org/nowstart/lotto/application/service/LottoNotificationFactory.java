package org.nowstart.lotto.application.service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.nowstart.lotto.domain.calendar.LottoDrawCalendar;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.CheckResult;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.LottoAccountSnapshot;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.LottoResult;
import org.nowstart.lotto.application.port.out.LoadLottoUsersPort.LottoUser;
import org.nowstart.lotto.application.port.out.SendNotificationPort.NotificationMessage;
import org.nowstart.lotto.domain.type.MessageType;
import org.nowstart.lotto.domain.type.TaskMode;

public class LottoNotificationFactory {

    public Optional<NotificationMessage> createCheckSuccessMessage(
            LottoUser user,
            LottoAccountSnapshot accountSnapshot,
            List<CheckResult> results
    ) {
        if (results.isEmpty()) {
            return Optional.empty();
        }

        CheckResult latestCheckResult = results.stream()
                .max(Comparator.comparingInt(this::roundNumber))
                .orElseThrow();
        var latestResult = latestCheckResult.result();
        return Optional.of(new NotificationMessage(
                "[" + user.id() + "] " + summary(latestResult),
                createResultMessageText(accountSnapshot, latestResult),
                latestCheckResult.detailImage(),
                user.email()
        ));
    }

    public NotificationMessage createFailureMessage(LottoUser user, TaskMode mode, Exception exception) {
        String failureMessage = String.format("""
                        [사용자: %s]
                        작업 실행 중 오류가 발생했습니다.
                        
                        오류 유형: %s
                        오류 메시지: %s
                        
                        자세한 내용은 서버 로그를 확인해 주세요.""",
                user.id(),
                exception.getClass().getSimpleName(),
                exception.getMessage()
        );

        return new NotificationMessage(
                "[" + user.id() + "] " + mode.getFailureSubject(),
                failureMessage,
                null,
                user.email()
        );
    }

    /** 구매 결과(성공·이미 구매·보류·거부·판정 불가 등)에 맞는 통지를 만든다. dry-run과 중단은 통지하지 않는다. */
    public Optional<NotificationMessage> createPurchaseOutcomeMessage(
            LottoUser user,
            LottoAccountSnapshot accountSnapshot,
            PurchaseOutcome outcome
    ) {
        return switch (outcome) {
            case PurchaseOutcome.Purchased purchased -> Optional.of(purchasedMessage(user, accountSnapshot, purchased));
            case PurchaseOutcome.AlreadyPurchased already -> Optional.of(alreadyPurchasedMessage(user, accountSnapshot, already));
            case PurchaseOutcome.NotOnSale notOnSale -> Optional.of(notice(user, "⏸️로또 판매 시간이 아닙니다", """
                    지금은 자동 구매를 하지 않는 시간입니다.
                    다음 구매 가능 시각: %s (%d회차)

                    판매 시간: 매일 06:00~24:00, 추첨일(토)은 20:00 마감
                    (마감 직전 10분인 토요일 19:50 이후에는 자동 구매를 시도하지 않습니다)"""
                    .formatted(format(notOnSale.nextPurchasableAt()), notOnSale.nextRound())));
            case PurchaseOutcome.Blocked blocked -> Optional.of(notice(user, "⏸️로또 구매 보류 — 수동 구매 금지", """
                    %d회차 직전 구매 시도의 결과가 아직 원장에 반영되지 않았을 수 있어, 이중 구매를 막기 위해 구매하지 않았습니다.
                    %s 이후에 다시 실행하세요. 그 전에 수동으로 구매하지 마세요."""
                    .formatted(blocked.round(), format(blocked.until()))));
            case PurchaseOutcome.Refused refused -> Optional.of(notice(user, TaskMode.PURCHASE.getFailureSubject(),
                    "구매하지 않았습니다.\n사유: " + refused.reason()));
            case PurchaseOutcome.Rejected rejected -> Optional.of(notice(user, TaskMode.PURCHASE.getFailureSubject(), """
                    %d회차 %d장 구매를 사이트가 거부했습니다(원장·예치금 변화 없음).
                    사이트 메시지: %s"""
                    .formatted(rejected.plan().round(), rejected.plan().toBuy(), String.join(" / ", rejected.siteMessages()))));
            case PurchaseOutcome.Uncertain uncertain -> Optional.of(notice(user, "❓로또 구매 확인 필요 — 수동 구매 금지", """
                    %d회차 %d장 최종 확정 클릭은 했지만, 원장과 예치금으로 구매를 확인하지 못했습니다.
                    이중 구매 위험이 있으니 수동으로 다시 구매하지 마세요.
                    %s 이후 결과 확인을 실행하거나 동행복권 구매내역을 확인해 주세요."""
                    .formatted(uncertain.plan().round(), uncertain.plan().toBuy(), format(uncertain.recheckAfter()))));
            case PurchaseOutcome.DryRun ignored -> Optional.empty();
            case PurchaseOutcome.Aborted ignored -> Optional.empty();
        };
    }

    private NotificationMessage purchasedMessage(
            LottoUser user,
            LottoAccountSnapshot accountSnapshot,
            PurchaseOutcome.Purchased purchased
    ) {
        String note = purchased.note() == null ? "" : "\n\n" + purchased.note();
        Optional<NotificationMessage> withTicket = createCheckSuccessMessage(user, accountSnapshot, purchased.tickets());
        if (withTicket.isPresent()) {
            NotificationMessage message = withTicket.get();
            return new NotificationMessage(message.subject(), message.text() + note, message.inlineImage(), message.to());
        }
        return notice(user, "✅로또6/45 " + purchased.plan().round() + "회차 구매 완료",
                accountSnapshotText(accountSnapshot) + "\n\n구매 수량: " + purchased.plan().toBuy() + "장" + note);
    }

    private NotificationMessage alreadyPurchasedMessage(
            LottoUser user,
            LottoAccountSnapshot accountSnapshot,
            PurchaseOutcome.AlreadyPurchased already
    ) {
        String rows = already.tickets().stream()
                .map(ticket -> "- " + ticket.result().date() + " " + ticket.result().count() + "장 " + ticket.result().result())
                .collect(Collectors.joining("\n"));
        byte[] image = already.tickets().stream()
                .map(CheckResult::detailImage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        return new NotificationMessage(
                "[" + user.id() + "] ✅로또6/45 " + already.plan().round() + "회차 이미 구매됨",
                """
                        %s

                        이번 회차는 이미 %d장 구매되어 추가로 구매하지 않았습니다.
                        %s""".formatted(accountSnapshotText(accountSnapshot), already.plan().alreadyPurchased(), rows),
                image,
                user.email()
        );
    }

    private NotificationMessage notice(LottoUser user, String subject, String text) {
        return new NotificationMessage("[" + user.id() + "] " + subject, text, null, user.email());
    }

    private static String format(TemporalAccessor time) {
        TemporalAccessor zoned = time instanceof Instant instant ? instant.atZone(LottoDrawCalendar.ZONE) : time;
        return DISPLAY_TIME.format(zoned);
    }

    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public NotificationMessage createInitializationMessage(LottoUser user, LottoAccountSnapshot accountSnapshot) {
        return new NotificationMessage(
                "⏳[" + user.id() + "] Lotto Init Test⏳",
                accountSnapshotText(accountSnapshot),
                null,
                user.email()
        );
    }

    private String createResultMessageText(LottoAccountSnapshot accountSnapshot, LottoResult result) {
        return """
                %s
                
                일자: %s
                회차: %s
                게임: %s
                번호: %s
                수량: %s
                결과: %s
                금액: %s
                """.formatted(
                accountSnapshotText(accountSnapshot),
                result.date(),
                result.round(),
                result.name(),
                result.number(),
                result.count(),
                result.result(),
                result.price()
        );
    }

    private String summary(LottoResult result) {
        MessageType messageType = MessageType.of(result.result());
        return messageType.getEmoji() + result.name() + " " + result.round() + "회차" + messageType.getEmoji();
    }

    private String accountSnapshotText(LottoAccountSnapshot accountSnapshot) {
        return accountSnapshot.name() + "의 💰예치금 : " + accountSnapshot.deposit();
    }

    private int roundNumber(CheckResult checkResult) {
        return Integer.parseInt(checkResult.result().round());
    }
}
