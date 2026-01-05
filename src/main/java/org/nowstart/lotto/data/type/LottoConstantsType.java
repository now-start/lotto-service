package org.nowstart.lotto.data.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LottoConstantsType {

    URL_LOGIN("https://dhlottery.co.kr/login", "URL", "로그인 페이지"),
    LOGIN_GROUP("LOGIN", "ARIA", "로그인 그룹 ARIA 라벨"),
    ID_INPUT("아이디", "SELECTOR", "ID 입력 필드"),
    PASSWORD_INPUT("비밀번호", "SELECTOR", "비밀번호 입력 필드"),
    LOGIN_LINK("로그인", "SELECTOR", "로그인 링크"),
    CHANGE_LATER("다음에 변경", "SELECTOR", "비밀번호 변경 미루기"),

    URL_MY_PAGE("https://dhlottery.co.kr/mypage/home", "URL", "마이페이지"),
    USER_NAME("#divUserNm", "SELECTOR", "사용자명"),
    USER_DEPOSIT("#totalAmt", "SELECTOR", "예치금"),

    RESULT_TABLE("https://dhlottery.co.kr/mypage/mylotteryledger", "URL", "구매/당첨 내역 페이지"),
    RESULT_CONTAINER("#winning-history-list", "SELECTOR", "구매/당첨 내역 컨테이너"),
    RESULT_ROW("#winning-history-list .whl-body > li.whl-row", "SELECTOR", "결과 행(리스트 아이템)"),
    RESULT_COL_DATE1(".whl-col.col-date1 .whl-txt", "SELECTOR", "구입일자 텍스트"),
    RESULT_COL_NAME(".whl-col.col-name .whl-txt", "SELECTOR", "복권명 텍스트"),
    RESULT_COL_ROUND(".whl-col.col-th .whl-txt", "SELECTOR", "회차 텍스트"),
    RESULT_COL_NUMBER(".whl-col.col-num .whl-txt.barcd", "SELECTOR", "선택번호/복권번호 텍스트"),
    RESULT_COL_COUNT(".whl-col.col-ea .whl-txt", "SELECTOR", "구입매수 텍스트"),
    RESULT_COL_RESULT(".whl-col.col-result .whl-txt", "SELECTOR", "당첨결과 텍스트"),
    RESULT_COL_PRICE(".whl-col.col-am .whl-txt", "SELECTOR", "당첨금 텍스트"),
    SEARCH_BUTTON("검색", "SELECTOR", "검색 버튼 라벨"),
    DATE_RANGE_THIRD_BTN("#containerBox > div.content-box-wrap > div > div > div > form > div.search-wrap.graybox.toggle-content > div > div:nth-child(2) > div > div.col-td > div.btn-wrap > button:nth-child(3)", "SELECTOR", "기간 버튼 3번째(사용자 지정 기간 등)"),


    URL_PURCHASE("https://el.dhlottery.co.kr/game/TotalGame.jsp?LottoId=LO40", "URL", "구매 페이지"),
    AUTO_NUMBER("자동번호발급 구매 수량 전체를 자동번호로 발급 받을 수 있습니다.", "SELECTOR", "자동번호발급"),
    QUANTITY_BOX("적용수량", "SELECTOR", "구매 수량 선택"),
    CONFIRM_BTN("확인", "SELECTOR", "확인 버튼"),
    PURCHASE_BTN("구매하기", "SELECTOR", "구매 버튼"),

    USER_AGENT_CHROME("Mozilla/5.0 (Windows NT 10.0; win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36", "USER_AGENT", "Chrome 브라우저 에뮬레이션"),
    USER_AGENT_EDGE("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 Edg/132.0.0.0", "USER_AGENT", "Edge 브라우저 에뮬레이션"),

    SCRIPT_CHROME_PLATFORM("Object.defineProperty(navigator, 'platform', { get: () => 'win64' });", "SCRIPT", "Chrome 플랫폼 스크립트"),
    SCRIPT_EDGE_PLATFORM("Object.defineProperty(navigator, 'platform', { get: () => 'Win32' });", "SCRIPT", "Edge 플랫폼 스크립트");

    private final String value;
    private final String type;
    private final String description;

    public String getFormattedValue(Object... params) {
        return String.format(value, params);
    }
}