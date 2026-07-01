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

| Key            | Description                                 | Example             |
|----------------|---------------------------------------------|---------------------|
| MAIL_USERNAME  | SMTP 이메일 아이디 (`spring.mail.username`)  | your_email_id       |
| MAIL_PASSWORD  | SMTP 이메일 비밀번호 (`spring.mail.password`) | your_email_password |
| LOTTO_ID       | 로또 사이트 아이디 (`lotto.users[*].id`)      | your_lotto_id       |
| LOTTO_PASSWORD | 로또 사이트 비밀번호 (`lotto.users[*].password`) | your_lotto_password |
| LOTTO_EMAIL    | 로또 결과 확인 이메일 (`lotto.users[*].email`) | your_email          |
| LOTTO_COUNT    | 로또 구매 개수 (`lotto.users[*].count`)       | 1 ~ 5               |

> 위 환경변수(`MAIL_*`, `LOTTO_*`)와 `lotto.*` 설정은 platform의 Spring Config Server
> (`spring.config.import`)에서 주입하는 것을 기본 전제로 합니다.
> 로컬에서 Config Server 없이 실행할 경우에는 별도 `application-local.yaml`(local 프로파일)을 두어 오버라이드합니다.

### 주요 `lotto.*` 튜닝 설정

| Key                                    | 기본값        | 설명                                          |
|----------------------------------------|--------------|---------------------------------------------|
| lotto.max-retries                      | 3            | 조회성 브라우저 작업(login/check) 재시도 횟수. 구매(buy)는 재시도하지 않음 |
| lotto.retry-delay-ms                   | 2000         | 재시도 지연(ms)                                   |
| lotto.purchase-result-timeout-ms       | 30000        | 구매 후 원장 확인 폴링 총 타임아웃(ms)                      |
| lotto.purchase-result-poll-interval-ms | 1000         | 구매 후 원장 확인 폴링 간격(ms)                         |
| lotto.max-concurrent-sessions          | 3            | 동시에 띄우는 Playwright 브라우저 세션 상한                 |
| lotto.user-task-timeout-ms             | 180000       | 사용자 1인 작업(login→구매→확인)의 최대 실행 시간(ms)          |
| lotto.trace-enabled                    | false        | Playwright 트레이스 저장 여부. 민감정보 캡처 위험으로 기본 비활성화   |
| lotto.cron.check                       | 0 0 22 * * 6 | 결과 확인 스케줄 cron                              |
| lotto.cron.buy                         | 0 0 9 * * 0  | 구매 스케줄 cron                                |
| lotto.cron.zone                        | Asia/Seoul   | 스케줄러 타임존                                   |

## API

수동 실행 API:

- `POST /api/lotto/check` : 결과 확인 실행
- `POST /api/lotto/buy` : 구매 + 결과 확인 실행

수동 실행 유저 지정:

- 전체 유저 실행(기본): `POST /api/lotto/check`, `POST /api/lotto/buy`
- 특정 유저 실행: `POST /api/lotto/check?userId=user1`
- 다중 유저 실행: `POST /api/lotto/buy?userId=user1&userId=user2`
- 유효하지 않은 `userId` 포함 시 `400 Bad Request` 반환

> 인증/인가는 애플리케이션 앞단의 게이트웨이에서 처리합니다. (서비스 자체에는 인증 로직을 두지 않습니다.)

## 실행 안전장치

- **구매 비멱등 보호**: 구매(`buy`)에는 재시도를 적용하지 않습니다. 최종 확정 이후 실패 시 중복 구매를 막기 위함이며, 실제 반영 여부는 구매 후 원장 확인으로 검증합니다.
- **중복 실행 방지**: 동일 사용자·모드 작업이 인스턴스 내에서 동시에 중복 실행되지 않도록 in-flight 가드를 둡니다. (다중 인스턴스 배포 시에는 분산 락/리더 선출이 별도로 필요합니다.)
- **동시성 상한**: `lotto.max-concurrent-sessions`로 동시에 뜨는 브라우저 세션 수를 제한합니다.
- **작업 타임아웃**: 사용자 작업이 `lotto.user-task-timeout-ms`를 초과하면 실패로 처리합니다.

## Config Refresh

- `LottoProperties`(`@ConfigurationProperties`)는 `POST /actuator/refresh` 시 재바인딩되어 `lotto.*` 변경값을 런타임에 반영합니다.
- 스케줄러는 고정 `@Scheduled`가 아닌 동적 Trigger(`SchedulingConfigurer`)로 동작하여, 매 다음 실행 계산 시점에 현재 `lotto.cron.*`(및 `lotto.cron.zone`)를 다시 읽습니다. 따라서 refresh 이후 변경된 cron이 다음 실행부터 반영됩니다.
- Actuator refresh endpoint: `POST /actuator/refresh`
