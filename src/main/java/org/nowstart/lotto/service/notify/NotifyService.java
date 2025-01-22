package org.nowstart.lotto.service.notify;

import jakarta.mail.MessagingException;

public interface NotifyService {
    void send(String message) throws MessagingException;
}
