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
ENV RESULT_SCHEDULE="0 0 * * 0"
ENV BUY_SCHEDULE="10 0 * * 0"

CMD ["/usr/bin/supervisord"]