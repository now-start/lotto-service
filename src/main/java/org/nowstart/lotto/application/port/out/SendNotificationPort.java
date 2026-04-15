package org.nowstart.lotto.application.port.out;

import org.nowstart.lotto.domain.model.NotificationMessage;

public interface SendNotificationPort {

    void send(NotificationMessage message);
}
