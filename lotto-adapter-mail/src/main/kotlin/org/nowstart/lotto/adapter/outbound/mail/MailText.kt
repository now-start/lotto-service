package org.nowstart.lotto.adapter.outbound.mail

/** 로그에 남기는 메일 주소를 가린다(앞 두 글자만 남김). */
internal fun maskAddress(address: String?): String {
    if (address.isNullOrBlank()) {
        return "<none>"
    }
    val at = address.indexOf('@')
    if (at <= 0) {
        return "***"
    }
    return address.substring(0, minOf(2, at)) + "***" + address.substring(at)
}

/** 본문을 HTML로 보낼 때 쓰는 최소 이스케이프. spring-web(HtmlUtils)에 의존하지 않기 위해 직접 둔다. */
internal fun escapeHtml(text: String): String = buildString(text.length) {
    for (ch in text) {
        when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(ch)
        }
    }
}
