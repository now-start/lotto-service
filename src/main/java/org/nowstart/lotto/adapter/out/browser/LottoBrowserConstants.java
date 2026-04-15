package org.nowstart.lotto.adapter.out.browser;

final class LottoBrowserConstants {

    static final String URL_LOGIN = "https://dhlottery.co.kr/login";
    static final String ID_INPUT = "아이디";
    static final String PASSWORD_INPUT = "비밀번호";
    static final String LOGIN_LINK = "#btnLogin";
    static final String CHANGE_LATER = "다음에 변경";

    static final String URL_MY_PAGE = "https://dhlottery.co.kr/mypage/home";
    static final String USER_NAME = "#divUserNm";
    static final String USER_DEPOSIT = "#totalAmt";

    static final String RESULT_TABLE = "https://dhlottery.co.kr/mypage/mylotteryledger";
    static final String RESULT_ROW = ".whl-row";
    static final String RESULT_COL_DATE1 = ".col-date1 .whl-txt";
    static final String RESULT_COL_NAME = ".col-name .whl-txt";
    static final String RESULT_COL_ROUND = ".col-th .whl-txt";
    static final String RESULT_COL_NUMBER = ".col-num .whl-txt";
    static final String RESULT_COL_COUNT = ".col-ea .whl-txt";
    static final String RESULT_COL_RESULT = ".col-result .whl-txt";
    static final String RESULT_COL_PRICE = ".col-am .whl-txt";
    static final String DETAIL_MODAL = "#Lotto645TicketP > div.pop-up > div";
    static final String CLOSE_DETAIL_BTN = "#Lotto645TicketP > div.pop-up > div > div.pop-head > button";

    static final String URL_PURCHASE = "https://ol.dhlottery.co.kr/olotto/game/game645.do";
    static final String AUTO_NUMBER = "#num2";
    static final String QUANTITY_BOX = "#amoundApply";
    static final String CONFIRM_BTN = "#btnSelectNum";
    static final String PURCHASE_BTN = "#btnBuy";
    static final String FINAL_CONFIRM_BTN = "#popupLayerConfirm > div > div.btns > input:nth-child(1)";

    static final String USER_AGENT_CHROME =
            "Mozilla/5.0 (Windows NT 10.0; win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36";
    static final String SCRIPT_CHROME_PLATFORM =
            "Object.defineProperty(navigator, 'platform', { get: () => 'win64' });";

    private LottoBrowserConstants() {
    }
}
