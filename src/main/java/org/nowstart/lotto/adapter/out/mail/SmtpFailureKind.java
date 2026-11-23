package org.nowstart.lotto.adapter.out.mail;

import jakarta.mail.MessagingException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.OptionalInt;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;

/** SMTP 발송 실패를 재발송 여부 기준으로 분류한다. */
enum SmtpFailureKind {

    /** 메시지 크기 초과(552). 첨부를 빼면 보낼 수 있다. */
    MESSAGE_TOO_LARGE,
    /** 접속 실패·타임아웃·4xx. 나중에 다시 보내면 된다. */
    TRANSIENT,
    /** 인증 실패·5xx. 다시 보내도 같은 결과다. */
    PERMANENT;

    private static final int SMTP_MESSAGE_TOO_LARGE = 552;

    static SmtpFailureKind classify(Throwable failure) {
        Deque<Throwable> pending = new ArrayDeque<>();
        Map<Throwable, Boolean> visited = new IdentityHashMap<>();
        pending.push(failure);
        while (!pending.isEmpty()) {
            Throwable current = pending.pop();
            if (visited.put(current, Boolean.TRUE) != null) {
                continue;
            }
            SmtpFailureKind kind = classifyOne(current);
            if (kind != null) {
                return kind;
            }
            if (current instanceof MailSendException sendException) {
                for (Exception messageException : sendException.getMessageExceptions()) {
                    pushIfPresent(pending, messageException);
                }
            }
            if (current instanceof MessagingException messagingException) {
                pushIfPresent(pending, messagingException.getNextException());
            }
            pushIfPresent(pending, current.getCause());
        }
        // 알 수 없는 실패는 알림을 잃지 않도록 재발송 대상으로 본다.
        return TRANSIENT;
    }

    private static void pushIfPresent(Deque<Throwable> pending, Throwable throwable) {
        if (throwable != null) {
            pending.push(throwable);
        }
    }

    private static SmtpFailureKind classifyOne(Throwable throwable) {
        if (throwable instanceof MailAuthenticationException
                || throwable instanceof MailParseException
                || throwable instanceof MailPreparationException) {
            return PERMANENT;
        }
        if (throwable instanceof MessagingException) {
            OptionalInt returnCode = smtpReturnCode(throwable);
            if (returnCode.isPresent()) {
                return byReturnCode(returnCode.getAsInt());
            }
        }
        if (throwable instanceof ConnectException
                || throwable instanceof SocketTimeoutException
                || throwable instanceof UnknownHostException
                || throwable instanceof SocketException) {
            return TRANSIENT;
        }
        return null;
    }

    /**
     * SMTP 응답 코드를 읽는다. 구현 클래스(Angus Mail의 SMTPSendFailedException 등)는 런타임 의존성이라
     * 컴파일 시점에 참조하지 않고 {@code getReturnCode()}를 리플렉션으로 호출한다.
     */
    private static OptionalInt smtpReturnCode(Throwable throwable) {
        try {
            Method getReturnCode = throwable.getClass().getMethod("getReturnCode");
            if (getReturnCode.getReturnType() == int.class) {
                return OptionalInt.of((int) getReturnCode.invoke(throwable));
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // 응답 코드가 없는 MessagingException
        }
        return OptionalInt.empty();
    }

    private static SmtpFailureKind byReturnCode(int returnCode) {
        if (returnCode == SMTP_MESSAGE_TOO_LARGE) {
            return MESSAGE_TOO_LARGE;
        }
        if (returnCode >= 500) {
            return PERMANENT;
        }
        return TRANSIENT;
    }
}
