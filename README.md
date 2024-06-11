# lotto

[![Build](https://github.com/now-start/lotto-service/actions/workflows/deploy.yaml/badge.svg)](https://github.com/now-start/lotto-service/actions/workflows/deploy.yaml)

로또 자동 구매

## Docker

docker-compose

```yaml
version: '3'
services:
  lotto-app:
    image: ghcr.io/now-start/lotto-service:latest
    environment:
      - LOTTO_ID=your_lotto_id
      - LOTTO_PASSWORD=your_lotto_password
      - LOTTO_NOTIFICATIONS=none

```

## Environment

| Key                 | Description                 | Example             |
|---------------------|-----------------------------|---------------------|
| LOTTO_ID            | 로또 사이트 아이디                  | your_lotto_id       |
| LOTTO_PASSWORD      | 로또 사이트 비밀번호                 | your_lotto_password |
| LOTTO_NOTIFICATIONS | 알림 옵션 (다중 설정 가능 `,` 구분해서 작성 | none, email, github |
| LOTTO_SCHEDULE      | 로또 스케줄 (m h dom mon dow)    | 0 0 * * 0           |

## Optional
### NOTIFICATIONS = github

| Key               | Description | Example                                 |
|-------------------|-------------|-----------------------------------------|
| GITHUB_TOKEN      | 깃허브 토큰      | your_github_token                       |
| GITHUB_REPOSITORY | 깃허브 저장소     | your_github_user/your_github_repository |

### NOTIFICATIONS = email

| Key                                | Description | Example            |
|------------------------------------|-------------|--------------------|
| NOTIFICATION_EMAIL_SERVER          | 이메일 서버 주소   | smtp.gmail.com     |
| NOTIFICATION_EMAIL_SERVER_PORT     | 이메일 서버 포트   | 587                |
| NOTIFICATION_EMAIL_SERVER_PASSWORD | 이메일 서버 비밀번호 | your_email_address |
| NOTIFICATION_EMAIL_TO              | 받는 사람       | email_address      |
| NOTIFICATION_EMAIL_FROM            | 보낸 사람       | your_email_address |
