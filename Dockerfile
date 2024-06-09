# Use multi-stage build
FROM python:3.9-slim-buster as builder

WORKDIR /app
ADD . /app

# Install dependencies and clean up in one RUN command
RUN apt-get update && apt-get install -y \
    wget \
    unzip \
    fontconfig \
    locales \
    gconf-service \
    libasound2 \
    libatk1.0-0 \
    libc6 \
    libcairo2 \
    libcups2 \
    libdbus-1-3 \
    libexpat1 \
    libfontconfig1 \
    libgcc1 \
    libgconf-2-4 \
    libgdk-pixbuf2.0-0 \
    libglib2.0-0 \
    libgtk-3-0 \
    libnspr4 \
    libpango-1.0-0 \
    libpangocairo-1.0-0 \
    libstdc++6 \
    libx11-6 \
    libx11-xcb1 \
    libxcb1 \
    libxcomposite1 \
    libxcursor1 \
    libxdamage1 \
    libxext6 \
    libxfixes3 \
    libxi6 \
    libxrandr2 \
    libxrender1 \
    libxss1 \
    libxtst6 \
    ca-certificates \
    fonts-liberation \
    libappindicator1 \
    libnss3 \
    lsb-release \
    xdg-utils \
    cron && \
    rm -rf /var/lib/apt/lists/* && \
    pip install --no-cache-dir -r requirements.txt && \
    python -m playwright install

# Install supervisor
RUN apt-get update && apt-get install -y supervisor

# Second stage
FROM python:3.9-slim-buster

WORKDIR /app
COPY --from=builder /app /app

# Add supervisor configuration file
COPY supervisord.conf /etc/supervisor/conf.d/supervisord.conf

RUN touch /var/log/cron.log

ENV RESULT_SCHEDULE="0 0 * * 0"
ENV BUY_SCHEDULE="10 0 * * 0"
RUN which supervisord
CMD ["/usr/local/bin/supervisord"]