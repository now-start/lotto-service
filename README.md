# lotto

[![Build](https://github.com/now-start/lotto-service/actions/workflows/deploy.yaml/badge.svg)](https://github.com/now-start/lotto-service/actions/workflows/deploy.yaml)

로또 자동 구매

## Docker

docker-compose

```yaml
services:
  lotto-app:
    user: root
    image: ghcr.io/now-start/lotto-service:latest
    environment:
      - TZ=Asia/Seoul
      - MAIL_USERNAME=your_email_id
      - MAIL_PASSWORD=your_email_password
      - LOTTO_ID=your_lotto_id
      - LOTTO_PASSWORD=your_lotto_password
      - LOTTO_EMAIL=your_email

```

## Environment

| Key            | Description   | Example             |
|----------------|---------------|---------------------|
| MAIL_USERNAME       | SMTP 이메일 아이디  | your_email_id       |
| MAIL_PASSWORD | SMTP 이메일 비밀번호 | your_email_password |
| LOTTO_ID       | 로또 사이트 아이디    | your_lotto_id       |
| LOTTO_PASSWORD | 로또 사이트 비밀번호   | your_lotto_password |
| LOTTO_EMAIL    | 로또 결과 확인 이메일  | your_email          |
| LOTTO_COUNT    | 로또 구매 개수      | 1 ~ 5               |
