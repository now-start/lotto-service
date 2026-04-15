package org.nowstart.lotto.domain.model;

public record NotificationMessage(
        String subject,
        String text,
        byte[] inlineImage,
        String to
) {
    public boolean hasInlineImage() {
        return inlineImage != null && inlineImage.length > 0;
    }
}
