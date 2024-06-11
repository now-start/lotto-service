from module.Buy import LottoBuyer
from module.Result import LottoResultChecker


def main():
  LottoResultChecker().run()
  LottoBuyer().run()


if __name__ == "__main__":
  main()
