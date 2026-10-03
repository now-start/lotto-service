package org.nowstart.lotto.adapter.out.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.Multipart;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.nowstart.lotto.application.port.out.SendNotificationPort.Attachment;
import org.nowstart.lotto.application.port.out.SendNotificationPort.NotificationMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

class NotificationMailAdapterTest {
    @Test
    void shouldSendTraceZipWithFailureText() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(mime);
        NotificationMailAdapter adapter = new NotificationMailAdapter(sender);
        ReflectionTestUtils.setField(adapter, "fromAddress", "sender@example.com");
        byte[] zip = {1, 2, 3};

        adapter.send(new NotificationMessage("failure", "error details", null, "user@example.com",
                new Attachment("lotto-trace.zip", zip)));

        mime.saveChanges();
        Multipart parts = (Multipart) mime.getContent();
        var attachment = parts.getBodyPart(parts.getCount() - 1);
        assertThat(attachment.getFileName()).isEqualTo("lotto-trace.zip");
        assertThat(attachment.getInputStream().readAllBytes()).isEqualTo(zip);
        verify(sender).send(mime);
    }
}
