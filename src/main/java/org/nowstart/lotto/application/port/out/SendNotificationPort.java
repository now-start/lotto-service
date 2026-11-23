package org.nowstart.lotto.application.port.out;

import java.util.Objects;

public interface SendNotificationPort {

    /**
     * 알림을 보낸다. 일시 장애로 즉시 보내지 못하면 재발송 대기열에 넣고 {@link Delivery#QUEUED}를 돌려준다.
     * 재발송으로도 해결되지 않는 영구 실패만 예외로 던진다.
     */
    Delivery send(NotificationMessage message);

    enum Delivery {
        SENT,
        QUEUED
    }

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

        public NotificationMessage {
            inlineImage = inlineImage == null ? null : inlineImage.clone();
        }

        public NotificationMessage withAttachment(Attachment attachment) {
            return new NotificationMessage(subject, text, inlineImage, to, attachment);
        }

        /** 첨부를 빼고, 뺀 이유를 본문 끝에 덧붙인다. */
        public NotificationMessage withoutAttachment(String reason) {
            if (attachment == null) {
                return this;
            }
            String note = "\n\n[첨부 생략] " + attachment.filename() + " (" + attachment.size() + " bytes): " + reason;
            return new NotificationMessage(subject, text + note, inlineImage, to, null);
        }

        @Override
        public byte[] inlineImage() {
            return inlineImage == null ? null : inlineImage.clone();
        }

        public boolean hasInlineImage() {
            return inlineImage != null && inlineImage.length > 0;
        }
    }

    record Attachment(String filename, byte[] content, String contentType) {

        public static final String ZIP = "application/zip";
        public static final String PNG = "image/png";

        public Attachment {
            Objects.requireNonNull(filename, "filename must not be null");
            Objects.requireNonNull(contentType, "contentType must not be null");
            content = content.clone();
        }

        public Attachment(String filename, byte[] content) {
            this(filename, content, ZIP);
        }

        @Override
        public byte[] content() {
            return content.clone();
        }

        public int size() {
            return content.length;
        }
    }
}
