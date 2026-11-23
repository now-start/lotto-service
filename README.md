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
> `bootRun`은 별도 지정이 없으면 `local` 프로파일로 실행됩니다.

### 주요 `lotto.*` 튜닝 설정

| Key                                    | 기본값        | 설명                                          |
|----------------------------------------|--------------|---------------------------------------------|
| lotto.max-retries                      | 3            | 조회성 브라우저 작업(login/check) 재시도 횟수. 구매(buy)는 재시도하지 않음 |
| lotto.retry-delay-ms                   | 2000         | 재시도 지연(ms)                                   |
| lotto.max-concurrent-sessions          | 3            | 동시에 띄우는 Playwright 브라우저 세션 상한                 |
| lotto.dry-run                          | false        | true면 최종 결제 클릭 직전에 멈춤 (local 프로파일 기본 true)   |
| lotto.user-task-timeout-ms             | 180000       | 사용자 1인 작업(login→구매→확인)의 최대 실행 시간(ms)          |
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

- **구매 전 원장 확인 (멱등 구매)**: 로그인 후 구매내역에서 이번 회차 로또6/45 구매 수량을 먼저 셉니다. `min(count, 5) - 이미 산 수량`만 사고, 0이면 결제 클릭 없이 "이미 구매됨" 메일(기존 티켓 이미지)을 보냅니다.
- **원장은 확실할 때만 신뢰**: 행도 "조회 결과가 없습니다" 표시도 없거나 행을 읽지 못하면 판독 불가로 보고 구매하지 않습니다.
- **판매 시간**: 매일 06:00~24:00, 추첨일(토)은 20:00 마감(자동 구매는 19:50까지). 판매 시간 밖의 구매 요청은 브라우저를 띄우지 않고 다음 판매 시각을 안내합니다. 회차는 1회차(2002-12-07)부터 주 단위로 계산합니다.
- **구매 판정**: 최종 확정 클릭 뒤에는 원장의 새 행(클릭 전후 비교), 예치금 차감, 사이트 거부 문구만으로 결과를 정합니다. 확인되지 않으면 "구매 확인 필요 — 수동 구매 금지" 메일을 보내고, 같은 회차 재구매를 10분간 막습니다.
- **구매 비멱등 보호**: 구매(`buy`)와 구매 판정용 원장 읽기에는 재시도를 적용하지 않습니다.
- **중복 실행 방지**: 같은 계정의 작업(확인·구매 모두)은 인스턴스 내에서 동시에 실행하지 않습니다. (다중 인스턴스 배포 시에는 분산 락/리더 선출이 별도로 필요합니다.)
- **동시성 상한**: `lotto.max-concurrent-sessions`로 동시에 뜨는 브라우저 세션 수를 제한합니다.
- **작업 타임아웃**: 사용자 작업이 `lotto.user-task-timeout-ms`를 초과하면 실패로 처리합니다.
- **실패 진단 자료**: Playwright trace는 **로그인 이후부터** DOM 스냅샷만 기록합니다(비밀번호 입력이 남지 않음). 실패 시 `lotto-trace-*.zip`으로 저장해 메일에 첨부하고, 로그인 전 실패는 화면 캡처(PNG)를 첨부합니다.
- **알림 유실 방지**: 첨부가 5MB를 넘거나 메일 서버가 크기 초과(552)로 거부하면 첨부 없이 보냅니다. 접속 실패 같은 일시 장애는 메모리 대기열에 넣어 10분마다 48시간까지 다시 보냅니다(재시작 시 대기열은 사라짐). 실패는 `lotto.notification.failed`, 대기열 크기는 `lotto.notification.outbox.size` 메트릭으로 볼 수 있습니다.

## Config

- `LottoProperties`(`@ConfigurationProperties`)는 기동 시 `lotto.*` 설정을 바인딩합니다.
- 스케줄러는 `@Scheduled`로 동작하며, `lotto.cron.*` 및 `lotto.cron.zone` 값은 애플리케이션 기동 시점에 적용됩니다.
