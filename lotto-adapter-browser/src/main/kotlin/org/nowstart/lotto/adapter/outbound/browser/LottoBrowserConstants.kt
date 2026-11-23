package org.nowstart.lotto.adapter.outbound.browser

import com.microsoft.playwright.BrowserType
import java.util.function.Supplier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

internal object LottoBrowserConstants {
    const val URL_LOGIN = "https://dhlottery.co.kr/login"
    const val ID_INPUT = "아이디"
    const val PASSWORD_INPUT = "비밀번호"
    const val LOGIN_LINK = "#btnLogin"
    const val CHANGE_LATER = "다음에 변경"
    const val URL_MY_PAGE = "https://dhlottery.co.kr/mypage/home"
    const val USER_NAME = "#divUserNm"
    const val USER_DEPOSIT = "#totalAmt"

    const val RESULT_TABLE = "https://dhlottery.co.kr/mypage/mylotteryledger"
    const val RESULT_ROW = ".whl-row"
    const val RESULT_COL_DATE1 = ".col-date1 .whl-txt"
    const val RESULT_COL_NAME = ".col-name .whl-txt"
    const val RESULT_COL_ROUND = ".col-th .whl-txt"
    const val RESULT_COL_NUMBER = ".col-num .whl-txt"
    const val RESULT_COL_COUNT = ".col-ea .whl-txt"
    const val RESULT_COL_RESULT = ".col-result .whl-txt"
    const val RESULT_COL_PRICE = ".col-am .whl-txt"
    const val DETAIL_MODAL = "#Lotto645TicketP > div.pop-up > div"
    const val CLOSE_DETAIL_BTN = "#Lotto645TicketP > div.pop-up > div > div.pop-head > button"

    const val URL_PURCHASE = "https://ol.dhlottery.co.kr/olotto/game/game645.do"
    const val AUTO_NUMBER = "#num2"
    const val QUANTITY_BOX = "#amoundApply"
    const val CONFIRM_BTN = "#btnSelectNum"
    const val PURCHASE_BTN = "#btnBuy"
    const val FINAL_CONFIRM_BTN = "#popupLayerConfirm > div > div.btns > input:nth-child(1)"

    const val USER_AGENT_CHROME =
        "Mozilla/5.0 (Windows NT 10.0; win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36"
    const val SCRIPT_CHROME_PLATFORM = "Object.defineProperty(navigator, 'platform', { get: () => 'win64' });"
}

@Configuration
class PlaywrightConfig {

    @Bean
    fun browserLaunchOptions(): Supplier<BrowserType.LaunchOptions> = Supplier { BrowserType.LaunchOptions().setHeadless(true) }
}
