import os

import requests
from playwright.sync_api import sync_playwright

LOTTO_ID = os.environ['LOTTO_ID']
LOTTO_PASSWORD = os.environ['LOTTO_PASSWORD']
GITHUB_TOKEN = os.environ['GITHUB_TOKEN']
GITHUB_REPOSITORY = os.environ['GITHUB_REPOSITORY']

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page()

    # Go to https://dhlottery.co.kr/user.do?method=login
    page.goto("https://dhlottery.co.kr/user.do?method=login")

    # Click [placeholder="아이디"]
    page.click("[placeholder=\"아이디\"]")

    # Fill [placeholder="아이디"]
    page.fill("[placeholder=\"아이디\"]", LOTTO_ID)

    # Press Tab
    page.press("[placeholder=\"아이디\"]", "Tab")

    # Fill [placeholder="비밀번호"]
    page.fill("[placeholder=\"비밀번호\"]", LOTTO_PASSWORD)

    # Press Tab
    page.press("[placeholder=\"비밀번호\"]", "Tab")

    # Press Enter
    # with page.expect_navigation(url="https://ol.dhlottery.co.kr/olotto/game/game645.do"):
    with page.expect_navigation():
        page.press("form[name=\"jform\"] >> text=로그인", "Enter")

    # time.sleep(5)

    page.goto("https://dhlottery.co.kr/userSsl.do?method=myPage")

    balance = page.query_selector("p.total_new > strong")
    lotto_data = []

    for i in range(2, 3):
        table = page.query_selector(
            f"table.tbl_data.tbl_data_col > tbody > tr:nth-child({i})")
        lotto_data.append({
            "date": table.query_selector("td:nth-child(1)").inner_text(),
            "rnd": table.query_selector("td:nth-child(2)").inner_text(),
            "result": table.query_selector("td:nth-child(6)").inner_text(),
            "reward": table.query_selector("td:nth-child(7)").inner_text()
        })

    url = f"https://api.github.com/repos/{GITHUB_REPOSITORY}/issues"
    headers = {
        "Authorization": f"Bearer {GITHUB_TOKEN}",
        "Accept": "application/vnd.github.v3+json"
    }

    response = requests.get(url, params={'state': 'open'}, headers=headers)
    issues = response.json()
    for issue in issues:
        url = f"https://api.github.com/repos/{GITHUB_REPOSITORY}/issues/{issue['number']}"
        for data in lotto_data:
            if data["rnd"] in issue["title"]:
                if '⌛' in issue["title"]:
                    if data["result"] == "당첨":
                        data = {
                            'title': f'로또6/45 {data["rnd"]}회차 구매 🎉',
                            'body': f'구매일: {data["date"]}\n잔액: {balance.inner_text()}원\n당첨금: {data["reward"]}',
                        }
                    elif data["result"] == "낙첨":
                        data = {
                            'title': f'로또6/45 {data["rnd"]}회차 구매 ☠️',
                            'body': f'구매일: {data["date"]}\n잔액: {balance.inner_text()}원\n당첨금: {data["reward"]}',
                        }
            else:
                data = {
                    "state": "closed"
                }
            requests.post(url, headers=headers, json=data)

    browser.close()
