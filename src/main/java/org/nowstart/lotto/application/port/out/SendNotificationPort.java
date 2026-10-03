package org.nowstart.lotto.application.port.out;

public interface SendNotificationPort {

    void send(NotificationMessage message);

    record NotificationMessage(
            String subject,
            String text,
            byte[] inlineImage,
            String to,
            Attachment attachment
    ) {

        public NotificationMessage(String subject, String text, byte[] inlineImage, String to) {
            this(subject, text, inlineImage, to, null);
        }

        public NotificationMessage withAttachment(Attachment attachment) {
            return new NotificationMessage(subject, text, inlineImage, to, attachment);
        }

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

    record Attachment(String filename, byte[] content) {
        public Attachment {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }
}
