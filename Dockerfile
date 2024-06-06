FROM python:3.10-slim-buster

WORKDIR /app
ADD . /app

RUN apt-get update && apt-get install -y cron
RUN pip install --no-cache-dir -r requirements.txt

COPY ./crontap /etc/cron.d/lotto-cron
RUN chmod 0644 /etc/cron.d/lotto-cron
RUN crontab /etc/cron.d/lotto-cron
RUN touch /var/log/cron.log

CMD cron && tail -f /var/log/cron.log