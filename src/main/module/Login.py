from playwright.sync_api import sync_playwright

from utils.Config import Config


class Login:
  def __init__(self):
    self.playwright = sync_playwright()
    self.browser = self.playwright.chromium.launch(headless=True)
    self.context = self.browser.new_context()

  def login(self, page):
    page.goto("https://dhlottery.co.kr/user.do?method=login")
    page.click("[placeholder=\"아이디\"]")
    page.fill("[placeholder=\"아이디\"]", Config.LOTTO_ID)
    page.press("[placeholder=\"아이디\"]", "Tab")
    page.fill("[placeholder=\"비밀번호\"]", Config.LOTTO_PASSWORD)
    page.press("[placeholder=\"비밀번호\"]", "Tab")
    with page.expect_navigation():
      page.press("form[name=\"jform\"] >> text=로그인", "Enter")

  def close(self):
    self.context.close()
    self.browser.close()
