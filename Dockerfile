# Build stage
FROM python:3.10-slim

WORKDIR /app
ADD . /app

# Install dependencies in a single RUN command to reduce image layers
RUN apt-get update && \
    apt-get install -y supervisor cron && \
    rm -rf /var/lib/apt/lists/* && \
    pip install --upgrade pip && \
    pip install -r requirements.txt && \
    python -m playwright install --with-deps

COPY supervisord.conf /etc/supervisor/conf.d/supervisord.conf

ENV TZ="Asia/Seoul"
ENV LOTTO_SCHEDULE="0 0 * * 0"
ENV LOTTO_NOTIFICATIONS="none"

CMD ["/usr/bin/supervisord"]