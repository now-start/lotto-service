package org.nowstart.lotto.application.port.out;

public interface SendNotificationPort {

    void send(NotificationMessage message);

    record NotificationMessage(
            String subject,
            String text,
            byte[] inlineImage,
            String to
    ) {

        public NotificationMessage {
            inlineImage = inlineImage == null ? null : inlineImage.clone();
        }

        @Override
        public byte[] inlineImage() {
            return inlineImage == null ? null : inlineImage.clone();
        }

        public boolean hasInlineImage() {
            return inlineImage != null && inlineImage.length > 0;
        }
    }
}
