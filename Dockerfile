FROM python:3.10-slim

WORKDIR /app
ADD . /app

RUN apt-get update && \
    apt-get install -y cron && \
    rm -rf /var/lib/apt/lists/* && \
    pip install --upgrade pip && \
    pip install -r requirements.txt && \
    python -m playwright install --with-deps chromium

RUN chmod +x /app/start.sh

ENV TZ="Asia/Seoul"
ENV LOTTO_SCHEDULE="0 0 * * 0"
ENV LOTTO_COUNT="5"
ENV LOTTO_NOTIFICATIONS="none"

CMD ["/bin/sh", "/app/start.sh"]