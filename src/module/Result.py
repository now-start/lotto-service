from src.module import Email
from src.utils.Config import Config
from src.module.Github import Github
from src.module.Login import Login


class LottoResultChecker(Login):
    def check_result(self, page):
        page.goto("https://dhlottery.co.kr/userSsl.do?method=myPage")
        balance = page.query_selector("p.total_new > strong").inner_text()
        round_list = []
        for i in range(1, 2):
            table = page.query_selector(
                f"table.tbl_data.tbl_data_col > tbody > tr:nth-child({i})")
            round_list.append({
                "date": table.query_selector("td:nth-child(1)").inner_text(),
                "round": table.query_selector("td:nth-child(2)").inner_text(),
                "result": table.query_selector("td:nth-child(6)").inner_text(),
                "reward": table.query_selector("td:nth-child(7)").inner_text()
            })
        return round_list, balance

    def run(self):
        try:
            page = self.context.new_page()
            self.login(page)
            round_list, balance = self.check_result(page)

            if "github" in Config.LOTTO_NOTIFICATIONS:
                Github.post_result(round_list, balance)
            if "email" in Config.LOTTO_NOTIFICATIONS:
                Email.post_result(round_list, balance)
        except Exception as e:
            print(f"결과 확인 실패: {e}")
            Email.post_error(e)
        finally:
            self.close()