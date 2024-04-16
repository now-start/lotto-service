FROM ubuntu:22.04

# Install dependencies
RUN apt update && apt install -y \
    python3 \
    python3-pip
RUN pip install playwright
RUN python3 -m playwright install --with-deps
RUN pip install requests

WORKDIR /app
COPY ../lotto-action/lotto_buy.py /app
