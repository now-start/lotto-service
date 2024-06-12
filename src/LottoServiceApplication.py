import sys
import os

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

from module.Buy import LottoBuyer
from module.Result import LottoResultChecker


def main():
  LottoResultChecker().run()
  LottoBuyer().run()


if __name__ == "__main__":
  main()
