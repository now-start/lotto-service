package org.nowstart.lotto.data.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LottoConstantsType {

    URL_LOGIN("https://dhlottery.co.kr/login", "로그인 페이지"),
    LOGIN_GROUP("LOGIN", "로그인 그룹 ARIA 라벨"),
    ID_INPUT("아이디", "ID 입력 필드"),
    PASSWORD_INPUT("비밀번호", "비밀번호 입력 필드"),
    LOGIN_LINK("#btnLogin", "로그인 링크"),
    CHANGE_LATER("다음에 변경", "비밀번호 변경 미루기"),

    URL_MY_PAGE("https://dhlottery.co.kr/mypage/home", "마이페이지"),
    USER_NAME("#divUserNm", "사용자명"),
    USER_DEPOSIT("#totalAmt", "예치금"),

    RESULT_TABLE("https://dhlottery.co.kr/mypage/mylotteryledger", "구매/당첨 내역 페이지"),
    //    DETAIL_BTN("#srchBtnToggle", "상세보기 버튼"),
//    DATE_RANGE_THIRD_BTN(
//            "#containerBox > div.content-box-wrap > div > div > div > form > div.search-wrap.graybox.toggle-content > div > div:nth-child(2) > div > div.col-td > div.btn-wrap > button:nth-child(3)",
//            "기간 버튼 3번째(사용자 지정 기간 등)"),
//    SEARCH_BUTTON("#btnSrch", "검색 버튼 라벨"),
    RESULT_ROW(".whl-row", "결과 행(리스트 아이템)"),
    RESULT_COL_DATE1(".col-date1 .whl-txt", "구입일자 텍스트"),
    RESULT_COL_NAME(".col-name .whl-txt", "복권명 텍스트"),
    RESULT_COL_ROUND(".col-th .whl-txt", "회차 텍스트"),
    RESULT_COL_NUMBER(".col-num .whl-txt", "선택번호/복권번호 텍스트"),
    RESULT_COL_COUNT(".col-ea .whl-txt", "구입매수 텍스트"),
    RESULT_COL_RESULT(".col-result .whl-txt", "당첨결과 텍스트"),
    RESULT_COL_PRICE(".col-am .whl-txt", "당첨금 텍스트"),
    DETAIL_MODAL("#Lotto645TicketP > div.pop-up > div", "상세 번호 팝업 모달"),
    CLOSE_DETAIL_BTN("#Lotto645TicketP > div.pop-up > div > div.pop-head > button", "상세 팝업 닫기 버튼"),

    URL_PURCHASE("https://ol.dhlottery.co.kr/olotto/game/game645.do", "구매 페이지"),
    AUTO_NUMBER("#num2", "자동번호발급"),
    QUANTITY_BOX("#amoundApply", "구매 수량 선택"),
    CONFIRM_BTN("#btnSelectNum", "확인 버튼"),
    PURCHASE_BTN("#btnBuy", "구매 버튼"),
    FINAL_CONFIRM_BTN("#popupLayerConfirm > div > div.btns > input:nth-child(1)", "최종 확인 버튼"),

    USER_AGENT_CHROME("Mozilla/5.0 (Windows NT 10.0; win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36",
            "Chrome 브라우저 에뮬레이션"),
    USER_AGENT_EDGE("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 Edg/132.0.0.0",
            "Edge 브라우저 에뮬레이션"),

    SCRIPT_CHROME_PLATFORM("Object.defineProperty(navigator, 'platform', { get: () => 'win64' });", "Chrome 플랫폼 스크립트"),
    SCRIPT_EDGE_PLATFORM("Object.defineProperty(navigator, 'platform', { get: () => 'Win32' });", "Edge 플랫폼 스크립트");

    private final String value;
    private final String description;
}