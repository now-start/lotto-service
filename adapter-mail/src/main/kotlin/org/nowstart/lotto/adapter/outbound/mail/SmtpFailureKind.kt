package org.nowstart.lotto.adapter.outbound.mail

import jakarta.mail.MessagingException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.IdentityHashMap
import org.springframework.mail.MailAuthenticationException
import org.springframework.mail.MailParseException
import org.springframework.mail.MailPreparationException
import org.springframework.mail.MailSendException

/** SMTP 발송 실패를 재발송 여부 기준으로 분류한다. */
enum class SmtpFailureKind {

    /** 메시지 크기 초과(552). 첨부를 빼면 보낼 수 있다. */
    MESSAGE_TOO_LARGE,

    /** 접속 실패·타임아웃·4xx. 나중에 다시 보내면 된다. */
    TRANSIENT,

    /** 인증 실패·메시지 구성 오류·5xx. 다시 보내도 같은 결과다. */
    PERMANENT;

    companion object {
        private const val SMTP_MESSAGE_TOO_LARGE = 552

        fun classify(failure: Throwable): SmtpFailureKind {
            val pending = ArrayDeque<Throwable>().apply { add(failure) }
            val visited = IdentityHashMap<Throwable, Boolean>()
            while (pending.isNotEmpty()) {
                val current = pending.removeFirst()
                if (visited.put(current, true) != null) {
                    continue
                }
                classifyOne(current)?.let { return it }
                if (current is MailSendException) {
                    current.messageExceptions.forEach { pending.add(it) }
                }
                if (current is MessagingException) {
                    current.nextException?.let { pending.add(it) }
                }
                current.cause?.let { pending.add(it) }
            }
            // 알 수 없는 실패는 알림을 잃지 않도록 재발송 대상으로 본다.
            return TRANSIENT
        }

        private fun classifyOne(throwable: Throwable): SmtpFailureKind? = when {
            throwable is MailAuthenticationException || throwable is MailParseException || throwable is MailPreparationException -> PERMANENT
            throwable is MessagingException -> smtpReturnCode(throwable)?.let(::byReturnCode)
            throwable is ConnectException || throwable is SocketTimeoutException ||
                throwable is UnknownHostException || throwable is SocketException -> TRANSIENT
            else -> null
        }

        /**
         * SMTP 응답 코드를 읽는다. 구현 클래스(Angus Mail의 SMTPSendFailedException 등)는 런타임 의존성이라
         * 컴파일 시점에 참조하지 않고 getReturnCode()를 리플렉션으로 호출한다.
         */
        private fun smtpReturnCode(throwable: Throwable): Int? = try {
            val getReturnCode = throwable.javaClass.getMethod("getReturnCode")
            if (getReturnCode.returnType == Int::class.javaPrimitiveType) getReturnCode.invoke(throwable) as Int else null
        } catch (exception: ReflectiveOperationException) {
            null
        }

        private fun byReturnCode(returnCode: Int): SmtpFailureKind = when {
            returnCode == SMTP_MESSAGE_TOO_LARGE -> MESSAGE_TOO_LARGE
            returnCode >= 500 -> PERMANENT
            else -> TRANSIENT
        }
    }
}
