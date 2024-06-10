from module.config import Config
from module.github import Github
from module.login import Login


class LottoResultChecker(Login):
    def check_result(self, page):
        page.goto("https://dhlottery.co.kr/userSsl.do?method=myPage")
        balance = page.query_selector("p.total_new > strong")
        lotto_data = []
        for i in range(1, 2):
            table = page.query_selector(
                f"table.tbl_data.tbl_data_col > tbody > tr:nth-child({i})")
            lotto_data.append({
                "date": table.query_selector("td:nth-child(1)").inner_text(),
                "rnd": table.query_selector("td:nth-child(2)").inner_text(),
                "result": table.query_selector("td:nth-child(6)").inner_text(),
                "reward": table.query_selector("td:nth-child(7)").inner_text()
            })
        return lotto_data, balance

    def run(self):
        try:
            page = self.context.new_page()
            self.login(page)
            lotto_data, balance = self.check_result(page)
        except Exception as e:
            print(f"결과 확인 실패: {e}")
            raise
        finally:
            self.close()

        if "github" in Config.LOTTO_NOTIFICATIONS:
            Github.post_result(lotto_data, balance)
        if "email" in Config.LOTTO_NOTIFICATIONS:
            pass


if __name__ == "__main__":
    LottoResultChecker().run()
