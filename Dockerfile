FROM python:3.10-slim

WORKDIR /app
ADD . /app

RUN apt-get update && \
    apt-get install -y cron && \
    rm -rf /var/lib/apt/lists/* && \
    pip install --upgrade pip && \
    pip install -r requirements.txt && \
    python -m playwright install --with-deps

RUN chmod +x /app/entrypoint.sh

ENV TZ="Asia/Seoul"
ENV PYTHONPATH="${PYTHONPATH}:/app"
ENV LOTTO_SCHEDULE="0 9 * * 0"
ENV LOTTO_COUNT="5"
ENV LOTTO_NOTIFICATIONS="none"

ENTRYPOINT ["/bin/sh", "/app/entrypoint.sh"]