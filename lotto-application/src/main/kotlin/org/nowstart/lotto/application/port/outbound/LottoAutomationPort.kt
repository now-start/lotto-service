package org.nowstart.lotto.application.port.outbound

import java.time.Duration

interface LottoAutomationPort {

    fun openSession(): LottoAutomationSession

    /** 로그인하고 이름·예치금을 읽는다. 진단 트레이스는 로그인 이후부터 기록한다. */
    fun login(session: LottoAutomationSession, user: LottoUser): LottoAccountSnapshot

    /** 이미 로그인된 세션에서 이름·예치금을 다시 읽는다(구매 후 예치금 차감 확인용). */
    fun readAccount(session: LottoAutomationSession): LottoAccountSnapshot

    /**
     * 구매내역(원장)을 읽는다. 행도 빈 결과 표시도 없거나 행을 해석하지 못하면
     * [org.nowstart.lotto.domain.exception.LedgerUnreadableException]을 던진다(fail-closed).
     */
    fun readLedger(session: LottoAutomationSession): List<LottoResult>

    /**
     * 조건을 만족할 때까지 원장을 다시 읽는다. 시간 안에 만족하지 않거나 중단 요청이 오면 null을 돌려준다.
     * 중간의 판독 실패는 다음 읽기로 넘어간다.
     */
    fun awaitLedger(
        session: LottoAutomationSession,
        condition: (List<LottoResult>) -> Boolean,
        timeout: Duration,
        abortRequested: () -> Boolean,
    ): List<LottoResult>?

    /** 원장 행들의 티켓 이미지를 캡처한다. 캡처에 실패한 행은 이미지 없이 돌려준다. */
    fun captureTickets(session: LottoAutomationSession, rows: List<LottoResult>): List<CheckResult>

    /**
     * 자동 번호로 [count]장을 구매 신청한다. 멱등하지 않으므로 절대 재시도하지 않는다.
     *
     * @param abortRequested 최종 확정 클릭 직전에 확인하는 중단 신호
     */
    fun buy(session: LottoAutomationSession, user: LottoUser, count: Int, abortRequested: () -> Boolean): PurchaseSubmission

    /** 결과 확인용: 원장의 모든 행과 티켓 이미지를 가져온다. */
    fun check(session: LottoAutomationSession): List<CheckResult>
}

interface LottoAutomationSession : AutoCloseable {

    fun markFailed() {}

    fun failureTrace(): Attachment? = null

    override fun close()
}

data class LottoAccountSnapshot(val name: String, val deposit: String)

data class LottoResult(
    val date: String,
    val round: String,
    val name: String,
    val number: String,
    val count: String,
    val result: String,
    val price: String,
) {
    /** 원장 행을 식별하는 키(클릭 전후 비교용). */
    val key: String
        get() = listOf(date, round, name, number, count, result, price)
            .joinToString("|") { it.trim().replace(WHITESPACE, " ") }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}

class CheckResult(val result: LottoResult, detailImage: ByteArray?) {

    private val image: ByteArray? = detailImage?.copyOf()

    val detailImage: ByteArray?
        get() = image?.copyOf()
}

/**
 * 구매 신청 결과. [Status.CLICKED]는 확정 클릭을 시도했다는 뜻일 뿐이며, 실제 구매 여부는 원장·예치금으로 판정해야 한다.
 *
 * @property dialogMessages 확정 클릭 이후 뜬 브라우저 대화상자 문구(사이트의 거부 사유 판단용)
 */
data class PurchaseSubmission(val status: Status, val dialogMessages: List<String> = emptyList()) {

    enum class Status {
        /** 최종 확정 전에 중단됨(구매 미수행이 확실함). */
        NOT_CLICKED,

        /** dry-run이라 최종 확정 직전에 멈춤(구매 미수행). */
        DRY_RUN,

        /** 최종 확정 클릭을 시도함(구매됐을 수 있음). */
        CLICKED,
    }
}
