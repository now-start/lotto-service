FROM python:3.10-slim-buster

WORKDIR /app
ADD . /app

RUN apt-get update && apt-get install -y cron && rm -rf /var/lib/apt/lists/*
RUN pip install --no-cache-dir -r requirements.txt
RUN playwright install

RUN touch /var/log/cron.log

ENV RESULT_SCHEDULE="0 0 * * 0"
ENV BUY_SCHEDULE="10 0 * * 0"

CMD echo "$RESULT_SCHEDULE python /app/lotto_result.py >> /var/log/cron.log 2>&1" | crontab - && \
    echo "$BUY_SCHEDULE python /app/lotto_buy.py >> /var/log/cron.log 2>&1" | crontab - && \
    cron && \
    tail -f /var/log/cron.log