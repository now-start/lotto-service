# lotto

[![Build and Push Docker Image](https://github.com/now-start/lotto-service/actions/workflows/build.yaml/badge.svg)](https://github.com/now-start/lotto-service/actions/workflows/build.yaml)

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

## API

수동 실행 API:

- `POST /api/lotto/check` : 결과 확인 실행
- `POST /api/lotto/buy` : 구매 + 결과 확인 실행

수동 실행 유저 지정:

- 전체 유저 실행(기본): `POST /api/lotto/check`, `POST /api/lotto/buy`
- 특정 유저 실행: `POST /api/lotto/check?userId=user1`
- 다중 유저 실행: `POST /api/lotto/buy?userId=user1&userId=user2`
- 유효하지 않은 `userId` 포함 시 `400 Bad Request` 반환

## Config Refresh

- `LottoProperties`는 `@RefreshScope`로 적용되어 `lotto.*` 변경값을 런타임에 반영합니다.
- 스케줄러는 고정 `@Scheduled`가 아닌 동적 Trigger 방식으로 동작하여 refresh 이후 변경된 cron을 다음 실행 계산부터 사용합니다.
- Actuator refresh endpoint 노출:
    - `POST /actuator/refresh`
    - `POST /actuator/busrefresh`
