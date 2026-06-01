package org.nowstart.lotto.adapter.out.browser;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.PurchaseReceipt;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.LottoResult;
import org.nowstart.lotto.domain.type.MessageType;

final class LottoPurchaseResultSelector {

    private static final DateTimeFormatter PURCHASE_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Pattern SEPARATED_DATE_PATTERN = Pattern.compile("(\\d{4})\\D+(\\d{1,2})\\D+(\\d{1,2})");

    private LottoPurchaseResultSelector() {
    }

    static Optional<LottoResult> selectNewPurchase(
            List<LottoResult> results,
            PurchaseReceipt purchaseReceipt
    ) {
        return results.stream()
                .filter(result -> matchesPurchasedCount(result, purchaseReceipt))
                .filter(result -> matchesPurchaseDate(result, purchaseReceipt))
                .filter(LottoPurchaseResultSelector::isPendingPurchase)
                .max(Comparator.comparingInt(LottoPurchaseResultSelector::roundNumber));
    }

    static String key(LottoResult result) {
        return String.join("|",
                normalize(result.date()),
                normalize(result.round()),
                normalize(result.name()),
                normalize(result.number()),
                normalize(result.count()),
                normalize(result.result()),
                normalize(result.price())
        );
    }

    private static boolean matchesPurchasedCount(LottoResult result, PurchaseReceipt purchaseReceipt) {
        return parseInteger(result.count()).orElse(-1) == purchaseReceipt.count();
    }

    private static boolean matchesPurchaseDate(LottoResult result, PurchaseReceipt purchaseReceipt) {
        return parseDate(result.date())
                .map(purchaseReceipt.purchaseDate()::equals)
                .orElse(false);
    }

    private static boolean isPendingPurchase(LottoResult result) {
        return MessageType.WAIT.getText().equals(result.result());
    }

    private static int roundNumber(LottoResult result) {
        return parseInteger(result.round()).orElse(0);
    }

    private static Optional<Integer> parseInteger(String text) {
        String digits = text == null ? "" : text.replaceAll("\\D", "");
        if (digits.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Integer.parseInt(digits));
    }

    private static Optional<LocalDate> parseDate(String text) {
        String source = text == null ? "" : text;
        Optional<LocalDate> separatedDate = parseSeparatedDate(source);
        if (separatedDate.isPresent()) {
            return separatedDate;
        }

        String digits = source.replaceAll("\\D", "");
        if (digits.length() < 8) {
            return Optional.empty();
        }

        try {
            return Optional.of(LocalDate.parse(digits.substring(0, 8), PURCHASE_DATE_FORMATTER));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    private static Optional<LocalDate> parseSeparatedDate(String text) {
        Matcher matcher = SEPARATED_DATE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }

        try {
            return Optional.of(LocalDate.of(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3))
            ));
        } catch (DateTimeException exception) {
            return Optional.empty();
        }
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim().replaceAll("\\s+", " ");
    }
}
