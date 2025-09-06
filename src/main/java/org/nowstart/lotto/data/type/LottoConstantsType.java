package org.nowstart.lotto.data.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LottoConstantsType {

    URL_LOGIN("https://dhlottery.co.kr/user.do?method=login", "URL", "로그인 페이지"),
    URL_MAIN("https://dhlottery.co.kr/common.do?method=main", "URL", "메인 페이지"),
    URL_MY_PAGE("https://dhlottery.co.kr/userSsl.do?method=myPage", "URL", "마이페이지"),
    URL_PURCHASE("https://ol.dhlottery.co.kr/olotto/game/game645.do", "URL", "구매 페이지"),
    URL_DETAIL_BASE("https://dhlottery.co.kr/myPage.do?method=lotto645Detail&orderNo=0000000000000000&barcode=%s&issueNo=1", "URL", "상세 페이지 베이스"),

    ID_INPUT("아이디", "SELECTOR", "ID 입력 필드"),
    PASSWORD_INPUT("비밀번호", "SELECTOR", "비밀번호 입력 필드"),
    USER_INFO("ul.information", "SELECTOR", "사용자 정보 영역"),

    USER_NAME("li:nth-child(1) > span > strong", "SELECTOR", "사용자명"),
    USER_DEPOSIT("li.money > a:nth-child(2) > strong", "SELECTOR", "예치금"),
    RESULT_TABLE("#article > div:nth-child(2) > div > div:nth-child(2) > table:nth-child(2) > tbody > tr", "SELECTOR", "결과 테이블"),
    
    CHANGE_LATER("다음에 변경", "SELECTOR", "비밀번호 변경 미루기"),
    LOGIN_LINK("로그인", "SELECTOR", "로그인 링크"),
    AUTO_NUMBER("자동번호발급 구매 수량 전체를 자동번호로 발급 받을 수 있습니다.", "SELECTOR", "자동번호발급"),
    QUANTITY_BOX("적용수량", "SELECTOR", "구매 수량 선택"),
    CONFIRM_BTN("확인", "SELECTOR", "확인 버튼"),
    PURCHASE_BTN("구매하기", "SELECTOR", "구매 버튼"),

    LOGIN_GROUP("LOGIN", "ARIA", "로그인 그룹 ARIA 라벨"),

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