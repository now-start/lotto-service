import requests

from src.utils.Config import GithubConfig


class Github:
  @staticmethod
  def post(url, headers, data):
    requests.post(url, headers=headers, json=data)

  @staticmethod
  def post_buy(rnd, date, balance):
    url = f"https://api.github.com/repos/{GithubConfig.GITHUB_REPOSITORY}/issues"
    headers = {
      'Authorization': f'token {GithubConfig.GITHUB_TOKEN}',
      'Accept': 'application/vnd.github.v3+json'
    }
    data = {
      'title': f'로또6/45 {rnd.inner_text()}회차 구매 ⌛',
      'body': f'구매일: {date.inner_text()}\n잔액: {balance.inner_text()}원'
    }
    Github.post(url, headers, data)

  @staticmethod
  def post_result(lotto_data, balance):
    url = f"https://api.github.com/repos/{GithubConfig.GITHUB_REPOSITORY}/issues"
    headers = {
      "Authorization": f"Bearer {GithubConfig.GITHUB_TOKEN}",
      "Accept": "application/vnd.github.v3+json"
    }
    response = requests.get(url, params={'state': 'open'}, headers=headers)
    issues = response.json()
    for issue in issues:
      url = f"https://api.github.com/repos/{GithubConfig.GITHUB_REPOSITORY}/issues/{issue['number']}"
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
        Github.post(url, headers, data)
