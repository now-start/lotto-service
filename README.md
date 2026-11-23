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
| lotto.max-concurrent-sessions          | 3            | 동시에 띄우는 Playwright 브라우저 세션 상한                 |
| lotto.dry-run                          | false        | true면 최종 결제 클릭 직전에 멈춤 (local 프로파일 기본 true)   |
| lotto.cron.check                       | 0 0 22 * * 6 | 결과 확인 스케줄 cron                              |
| lotto.cron.buy                         | 0 0 9 * * 0  | 구매 스케줄 cron                                |

> `lotto.max-retries`, `lotto.retry-delay-ms`, `lotto.user-task-timeout-ms`, `lotto.cron.zone`, `lotto.purchase-result-*`는 더 이상 쓰지 않습니다(Config Server에 남아 있어도 무시됩니다). 재시도 간격·판매 시간 같은 규칙은 코드 상수이고 스케줄 시간대는 Asia/Seoul 고정입니다.

## API

수동 실행 API:

- `POST /api/lotto/check` : 결과 확인 작업 시작
- `POST /api/lotto/buy` : 구매 작업 시작
- `GET /api/lotto/executions/{id}` : 계정별 진행 상태(상태, 시도 횟수, 다음 시도 시각, 마지막 오류)
- `GET /api/lotto/executions` : 최근 실행 목록(24시간, 최대 50건)

작업은 백그라운드에서 돌고 요청은 즉시 응답합니다.

- `202 Accepted` + `Location: /api/lotto/executions/{id}` : 작업을 시작함
- `409 Conflict` : 요청한 계정의 같은 작업이 모두 이미 진행 중(응답에 기존 실행 id)
- 결과는 작업이 끝났을 때 메일로 한 번만 보냅니다.

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
- **작업 단위 재시도 (사이트 장애 자동 복구)**: 실패하면 새 브라우저 세션으로 처음부터(로그인 → 구매 전 원장 확인 → 구매) 다시 시도합니다. 간격은 5분부터 두 배씩 최대 60분, 최대 12회이며 구매는 해당 회차 토요일 19:50까지입니다. 구매 전 원장 확인이 이미 산 수량을 빼므로 재시도해도 이중 구매가 되지 않습니다. 판매 시간 밖이면 다음 판매 시작에 실행합니다(시도 횟수에 넣지 않음).
- **로그인 거부 보호**: 로그인 제출 후에도 폼이 그대로면 거부로 보고, 2회 연속이면 계정 잠김을 막기 위해 멈춥니다(시간 초과 같은 사이트 장애는 계속 재시도).
- **재시작 직후 보류**: 기동 후 10분간은 구매를 미룹니다(재시작으로 판정 대기 기록이 사라졌을 수 있음).
- **중복 실행 방지**: 같은 계정의 작업(확인·구매 모두)은 인스턴스 내에서 동시에 실행하지 않습니다. (다중 인스턴스 배포 시에는 분산 락/리더 선출이 별도로 필요합니다.)
- **동시성 상한**: `lotto.max-concurrent-sessions`로 동시에 뜨는 브라우저 세션 수를 제한합니다.
- **시도 감시**: 한 번의 시도가 15분을 넘기면 중단 신호를 보내 결제 직전이면 멈추고, 다음 시도로 넘깁니다.
- **정상 종료**: 종료 시 새 시도를 막고 진행 중인 시도를 최대 60초 기다립니다. 진행 중이던 작업 상태는 메모리에만 있어 재시작하면 사라집니다.
- **실패 진단 자료**: Playwright trace는 **로그인 이후부터** DOM 스냅샷만 기록합니다(비밀번호 입력이 남지 않음). 실패 시 `lotto-trace-*.zip`으로 저장해 메일에 첨부하고, 로그인 전 실패는 화면 캡처(PNG)를 첨부합니다.
- **알림 유실 방지**: 첨부가 5MB를 넘거나 메일 서버가 크기 초과(552)로 거부하면 첨부 없이 보냅니다. 접속 실패 같은 일시 장애는 메모리 대기열에 넣어 10분마다 48시간까지 다시 보냅니다(재시작 시 대기열은 사라짐). 실패는 `lotto.notification.failed`, 대기열 크기는 `lotto.notification.outbox.size` 메트릭으로 볼 수 있습니다.

## 구조

Kotlin + Gradle 멀티모듈입니다. 루트 프로젝트가 실행 앱(`bootJar`/`bootBuildImage`)이고, 레이어는 하위 모듈로 나뉩니다.

| 모듈 | 내용 |
|------|------|
| `lotto-domain` | 순수 Kotlin 도메인: 판매 달력·회차, 원장 계산, 타입·예외 |
| `lotto-application` | 포트(inbound/outbound)와 서비스(구매 절차, 작업 실행, 알림 생성) |
| `lotto-adapter-web` | 수동 실행 API |
| `lotto-adapter-scheduler` | 크론 스케줄, 기동 초기화 |
| `lotto-adapter-browser` | Playwright 자동화 |
| `lotto-adapter-mail` | 메일 발송·재발송 대기열 |

자세한 구조는 [docs/clean-architecture.md](docs/clean-architecture.md)를 참고하세요.

## Config

- `LottoProperties`(`@ConfigurationProperties`)는 기동 시 `lotto.*` 설정을 바인딩합니다.
- 스케줄러는 `@Scheduled`로 동작하며, `lotto.cron.*` 값은(시간대 Asia/Seoul 고정) 애플리케이션 기동 시점에 적용됩니다.
