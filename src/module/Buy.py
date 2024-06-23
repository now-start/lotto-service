import time

from src.module import Email
from src.utils.Config import Config
from src.module.Github import Github
from src.module.Login import Login


class LottoBuyer(Login):
    def buy_lotto(self, page):
        page.goto(url="https://ol.dhlottery.co.kr/olotto/game/game645.do")
        page.locator("#popupLayerAlert").get_by_role("button",
                                                     name="확인").click()
        page.click("text=자동번호발급")
        page.select_option("select", Config.LOTTO_COUNT)
        page.click("text=확인")
        page.click("input:has-text(\"구매하기\")")

    def run(self):
        try:
            page = self.context.new_page()
            self.login(page)
            time.sleep(5)
            self.buy_lotto(page)
            time.sleep(2)
            page.click("text=확인 취소 >> input[type=\"button\"]")
            page.click("input[name=\"closeLayer\"]")
        except Exception as e:
            print(f"구매 실패: {e}")
            raise
        finally:
            page.close()

        page = self.context.new_page()
        page.goto("https://dhlottery.co.kr/userSsl.do?method=myPage")
        balance = page.query_selector("p.total_new > strong").inner_text()
        table = page.query_selector(
            "table.tbl_data.tbl_data_col > tbody > tr:nth-child(1)")
        round_list= {
            "date": table.query_selector("td:nth-child(1)").inner_text(),
            "round": table.query_selector("td:nth-child(2)").inner_text()
        }

        if "github" in Config.LOTTO_NOTIFICATIONS:
            Github.post_buy(round_list, balance)
        if "email" in Config.LOTTO_NOTIFICATIONS:
            Email.post_buy(round_list, balance)

        self.close()