import smtplib
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText

from src.utils.Config import EmailConfig

def create_email(subject, body):
    message = MIMEMultipart()
    message['From'] = EmailConfig.NOTIFICATION_EMAIL_FROM
    message['To'] = EmailConfig.NOTIFICATION_EMAIL_TO
    message['Subject'] = subject
    message.attach(MIMEText(body, 'plain'))
    return message

def send_email(message):
    session = smtplib.SMTP(EmailConfig.NOTIFICATION_EMAIL_SERVER, EmailConfig.NOTIFICATION_EMAIL_SERVER_PORT)
    session.starttls()
    session.login(EmailConfig.NOTIFICATION_EMAIL_FROM, EmailConfig.NOTIFICATION_EMAIL_SERVER_PASSWORD)
    text = message.as_string()
    session.sendmail(EmailConfig.NOTIFICATION_EMAIL_FROM, EmailConfig.NOTIFICATION_EMAIL_TO, text)
    session.quit()

def post_buy(round_list, balance):
    subject = f'로또6/45 {round_list["round"]}회차 구매 ⌛'
    body = f'구매일: {round_list["date"]}\n잔액: {balance}원'
    message = create_email(subject, body)
    send_email(message)

def post_result(round_list, balance):
    if round_list[0]["result"] == "당첨":
        subject = f'로또6/45 {round_list[0]["round"]}회차 구매 🎉'
    elif round_list[0]["result"] == "낙첨":
        subject = f'로또6/45 {round_list[0]["round"]}회차 구매 ☠️'
    else:
        subject = f'로또6/45 {round_list[0]["round"]}회차 구매 ⌛️'
    body = f'구매일: {round_list[0]["date"]}\n잔액: {balance}원\n당첨금: {round_list[0]["reward"]}'
    message = create_email(subject, body)
    send_email(message)

if __name__ == "__main__":
    subject = f'테스트 이메일 입니다'
    body = (f'스케줄 : {EmailConfig.LOTTO_SCHEDULE}\n'
            f'로또 구매 횟수 : {EmailConfig.LOTTO_COUNT}')
    message = create_email(subject, body)
    send_email(message)