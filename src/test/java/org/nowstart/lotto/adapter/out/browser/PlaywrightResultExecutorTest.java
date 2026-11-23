package org.nowstart.lotto.adapter.out.browser;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nowstart.lotto.application.port.out.LottoAutomationPort.LottoResult;
import org.nowstart.lotto.domain.exception.LedgerUnreadableException;

@DisplayName("구매 판정용 원장 읽기 (fail-closed)")
class PlaywrightResultExecutorTest {

    private final PlaywrightResultExecutor executor = new PlaywrightResultExecutor();
    private final Page page = mock(Page.class);
    private final Locator rows = mock(Locator.class);
    private final Locator pageMarker = mock(Locator.class);
    private final Locator visibleMarker = mock(Locator.class);
    private final Locator firstContent = mock(Locator.class);

    @BeforeEach
    void setUp() {
        Locator visibleRows = mock(Locator.class);
        Locator rowOrMarker = mock(Locator.class);
        given(page.locator(LottoBrowserConstants.RESULT_ROW)).willReturn(rows);
        given(rows.locator(PlaywrightResultExecutor.VISIBLE_ONLY)).willReturn(visibleRows);
        given(page.getByText(PlaywrightResultExecutor.EMPTY_LEDGER_TEXT)).willReturn(pageMarker);
        given(pageMarker.locator(PlaywrightResultExecutor.VISIBLE_ONLY)).willReturn(visibleMarker);
        given(visibleRows.or(visibleMarker)).willReturn(rowOrMarker);
        given(rowOrMarker.first()).willReturn(firstContent);
    }

    @Test
    @DisplayName("보이는 행도 빈 결과 표시도 나타나지 않으면 판독 불가로 본다 (표가 늦게 채워지는 경우)")
    void shouldFailWhenLedgerContentNeverAppears() {
        willThrow(new TimeoutError("Timeout 15000ms exceeded")).given(firstContent).waitFor(any(Locator.WaitForOptions.class));

        thenThrownBy(() -> executor.readLedger(page)).isInstanceOf(LedgerUnreadableException.class);
    }

    @Test
    @DisplayName("행도 빈 결과 표시도 없으면 0행으로 보지 않고 판독 불가로 본다 (로그인 풀림·로딩 실패)")
    void shouldFailWhenNoRowsAndNoEmptyMarker() {
        given(rows.count()).willReturn(0);
        given(visibleMarker.count()).willReturn(0);

        thenThrownBy(() -> executor.readLedger(page)).isInstanceOf(LedgerUnreadableException.class);
    }

    @Test
    @DisplayName("빈 결과 표시 행이 있으면 0행으로 인정한다 (신규 계정)")
    void shouldAcceptEmptyMarkerRow() {
        Locator row = mock(Locator.class);
        given(rows.count()).willReturn(1);
        given(rows.nth(0)).willReturn(row);
        given(row.innerText()).willReturn("조회 결과가 없습니다.");

        then(executor.readLedger(page)).isEmpty();
    }

    @Test
    @DisplayName("보이는 빈 결과 표시가 행이 아닌 곳에 있어도 0행으로 인정한다")
    void shouldAcceptEmptyMarkerElsewhereOnPage() {
        given(rows.count()).willReturn(0);
        given(visibleMarker.count()).willReturn(1);

        then(executor.readLedger(page)).isEmpty();
    }

    @Test
    @DisplayName("숨겨진 빈 결과 문구만 있으면 0행으로 인정하지 않는다")
    void shouldIgnoreHiddenEmptyMarker() {
        given(rows.count()).willReturn(0);
        given(pageMarker.count()).willReturn(1);
        given(visibleMarker.count()).willReturn(0);

        thenThrownBy(() -> executor.readLedger(page)).isInstanceOf(LedgerUnreadableException.class);
    }

    @Test
    @DisplayName("행을 하나라도 읽지 못하면 건너뛰지 않고 판독 불가로 본다")
    void shouldFailWhenAnyRowIsUnreadable() {
        Locator row = mock(Locator.class);
        given(rows.count()).willReturn(1);
        given(rows.nth(0)).willReturn(row);
        given(row.innerText()).willThrow(new PlaywrightException("element detached"));

        thenThrownBy(() -> executor.readLedger(page)).isInstanceOf(LedgerUnreadableException.class);
    }

    @Test
    @DisplayName("원장 행의 각 칸을 읽는다")
    void shouldReadRowColumns() {
        Locator row = mock(Locator.class);
        given(rows.count()).willReturn(1);
        given(rows.nth(0)).willReturn(row);
        given(row.innerText()).willReturn("2026-11-22 로또6/45 1252 ...");
        column(row, LottoBrowserConstants.RESULT_COL_DATE1, "2026-11-22");
        column(row, LottoBrowserConstants.RESULT_COL_ROUND, "1252");
        column(row, LottoBrowserConstants.RESULT_COL_NAME, "로또6/45");
        column(row, LottoBrowserConstants.RESULT_COL_NUMBER, "12345 67890");
        column(row, LottoBrowserConstants.RESULT_COL_COUNT, "5");
        column(row, LottoBrowserConstants.RESULT_COL_RESULT, "미추첨");
        column(row, LottoBrowserConstants.RESULT_COL_PRICE, "5,000");

        List<LottoResult> results = executor.readLedger(page);

        then(results).containsExactly(new LottoResult("2026-11-22", "1252", "로또6/45", "1234567890", "5", "미추첨", "5,000"));
    }

    private void column(Locator row, String selector, String text) {
        Locator cell = mock(Locator.class);
        given(row.locator(selector)).willReturn(cell);
        given(cell.innerText()).willReturn(text);
    }
}
