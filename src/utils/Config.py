import os


class Config:
  LOTTO_ID = os.environ['LOTTO_ID']
  LOTTO_PASSWORD = os.environ['LOTTO_PASSWORD']
  LOTTO_NOTIFICATIONS = os.environ['LOTTO_NOTIFICATIONS']
  COUNT = 5


class GithubConfig(Config):
  GITHUB_TOKEN = os.environ['GITHUB_TOKEN']
  GITHUB_REPOSITORY = os.environ['GITHUB_REPOSITORY']


class EmailConfig(Config):
  NOTIFICATION_EMAIL_SERVER = os.environ['NOTIFICATION_EMAIL_SERVER']
  NOTIFICATION_EMAIL_SERVER_PORT = os.environ['NOTIFICATION_EMAIL_SERVER_PORT']
  NOTIFICATION_EMAIL_SERVER_PASSWORD = os.environ[
    'NOTIFICATION_EMAIL_SERVER_PASSWORD']
  NOTIFICATION_EMAIL_TO = os.environ['NOTIFICATION_EMAIL_TO']
  NOTIFICATION_EMAIL_FROM = os.environ['NOTIFICATION_EMAIL_FROM']