import requests

from src.utils.Config import GithubConfig


class Github:
  @staticmethod
  def post(url, headers, data):
    requests.post(url, headers=headers, json=data)

  @staticmethod
  def post_buy(round_list, balance):
    url = f"https://api.github.com/repos/{GithubConfig.GITHUB_REPOSITORY}/issues"
    headers = {
      'Authorization': f'token {GithubConfig.GITHUB_TOKEN}',
      'Accept': 'application/vnd.github.v3+json'
    }
    data = {
      'title': f'로또6/45 {round_list["round"]}회차 구매 ⌛',
      'body': f'구매일: {round_list["date"]}\n잔액: {balance.inner_text()}원'
    }
    Github.post(url, headers, data)

  @staticmethod
  def post_result(round_list, balance):
    url = f"https://api.github.com/repos/{GithubConfig.GITHUB_REPOSITORY}/issues"
    headers = {
      "Authorization": f"Bearer {GithubConfig.GITHUB_TOKEN}",
      "Accept": "application/vnd.github.v3+json"
    }
    response = requests.get(url, params={'state': 'open'}, headers=headers)
    issues = response.json()
    for issue in issues:
      url = f"https://api.github.com/repos/{GithubConfig.GITHUB_REPOSITORY}/issues/{issue['number']}"
      for round in round_list:
        if round["round"] in issue["title"]:
          if '⌛' in issue["title"]:
            if round["result"] == "당첨":
              round = {
                'title': f'로또6/45 {round["round"]}회차 구매 🎉',
                'body': f'구매일: {round["date"]}\n잔액: {balance.inner_text()}원\n당첨금: {round["reward"]}',
              }
            elif round["result"] == "낙첨":
              round = {
                'title': f'로또6/45 {round["round"]}회차 구매 ☠️',
                'body': f'구매일: {round["date"]}\n잔액: {balance.inner_text()}원\n당첨금: {round["reward"]}',
              }
        else:
          round = {
            "state": "closed"
          }
        Github.post(url, headers, round)
