# lotto

[![Build and Push Docker Image](https://github.com/now-start/lotto-service/actions/workflows/build.yaml/badge.svg)](https://github.com/now-start/lotto-service/actions/workflows/build.yaml)

로또 자동 구매

## Docker

Config Server에 접근할 수 있는 환경에서 `docker compose up -d`로 실행합니다. 컨테이너 시간대는 UTC이며, 구매·조회 스케줄과 회차 계산은 서울 시간을 사용합니다.

## 운영 설정

`local` 이외의 프로파일에서는 Spring Config Server에서 `lotto` 앱의 실행 설정을 읽습니다. 기본 접속 주소는 `https://spring.nowstart.org/config`이며, 다른 서버를 쓸 때는 `SPRING_CONFIG_IMPORT=configserver:<주소>`로 지정합니다. Config Server에서 설정을 가져오지 못하면 기동이 실패합니다.

| Config Server 키 | 설정값 |
|---|---|
| `lotto.users` | 한 계정 이상. 계정마다 `id`, `password`, 알림을 받을 `email`이 필요합니다. `count`는 회차당 구매 수량(1~5, 기본 5), `init`은 기동 시 로그인 여부(기본 false)입니다. |
| `lotto.cron.check`, `lotto.cron.buy` | 결과 확인과 구매에 사용할 cron 표현식. 예를 들어 `0 0 22 * * 6`은 토요일 22시 확인, `0 0 9 * * 0`은 일요일 9시 구매입니다. 시간대는 Asia/Seoul입니다. |
| `lotto.dry-run` | `true`이면 최종 결제 클릭 직전에 멈춥니다. 기본값은 `false`입니다. |
| `spring.mail.host`, `spring.mail.port`, `spring.mail.username`, `spring.mail.password` | 알림을 보낼 SMTP 서버의 접속 정보. 인증과 STARTTLS는 메일 서버에 맞춰 `spring.mail.properties.mail.smtp.auth`, `spring.mail.properties.mail.smtp.starttls.enable`로 설정합니다. |
| `spring.mail.properties.mail.smtp.connectiontimeout`, `spring.mail.properties.mail.smtp.timeout`, `spring.mail.properties.mail.smtp.writetimeout` | SMTP 연결·읽기·쓰기 제한을 밀리초 단위로 지정합니다. |
| `lotto.mail.from` | 알림 메일의 발신 주소. 기본값은 `no-reply@nowstart.org`입니다. |

## 로컬 실행

`./gradlew bootRun`은 기본적으로 `local` 프로파일을 사용합니다. 이때 `application-local.yaml`이 아래 환경변수를 읽으며 `lotto.dry-run`의 기본값은 `true`입니다.

| Key            | Description                                 | Example             |
|----------------|---------------------------------------------|---------------------|
| MAIL_USERNAME  | SMTP 이메일 아이디 (`spring.mail.username`)  | your_email_id       |
| MAIL_PASSWORD  | SMTP 이메일 비밀번호 (`spring.mail.password`) | your_email_password |
| LOTTO_ID       | 로또 사이트 아이디 (`lotto.users[*].id`)      | your_lotto_id       |
| LOTTO_PASSWORD | 로또 사이트 비밀번호 (`lotto.users[*].password`) | your_lotto_password |
| LOTTO_EMAIL    | 로또 결과 확인 이메일 (`lotto.users[*].email`) | your_email          |
| LOTTO_COUNT    | 로또 구매 개수 (`lotto.users[*].count`)       | 1 ~ 5               |

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
- **판매 시간**은 매일 06:00~24:00이며 토요일에는 20:00에 끝납니다. 자동 구매는 대상 회차의 토요일 19:50에 마감합니다. 판매가 잠시 중단된 동안에는 같은 회차의 다음 판매 시간까지 기다립니다. 대기 중 회차가 마감되면 해당 작업은 종료됩니다.
- **구매 판정**은 최종 확정 클릭 전후의 원장 비교, 예치금 차감, 사이트 거부 문구로 합니다. 클릭 후 결과가 불확실하면 같은 회차 재구매를 10분간 보류하고 기한 안에 원장을 다시 확인합니다. 중간 상태는 실행 조회 API에서 확인할 수 있고, 메일은 작업이 끝날 때 보냅니다.
- **작업 단위 재시도 (사이트 장애 자동 복구)**: 실패하면 새 브라우저 세션으로 처음부터(로그인 → 구매 전 원장 확인 → 구매) 다시 시도합니다. 간격은 5분부터 두 배씩 최대 60분, 최대 12회이며 구매는 해당 회차 토요일 19:50까지입니다. 구매 전 원장 확인이 이미 산 수량을 빼므로 재시도해도 이중 구매가 되지 않습니다. 판매 시간 밖이면 다음 판매 시작에 실행합니다(시도 횟수에 넣지 않음).
- **로그인 거부 보호**: 로그인 제출 후에도 폼이 그대로면 거부로 보고, 2회 연속이면 계정 잠김을 막기 위해 멈춥니다(시간 초과 같은 사이트 장애는 계속 재시도).
- **재시작 직후 보류**: 기동 후 10분간은 구매를 미룹니다(재시작으로 판정 대기 기록이 사라졌을 수 있음).
- 서로 다른 계정은 **각자의 브라우저 세션**에서 동시에 작업할 수 있습니다. 한 인스턴스에서 같은 계정의 초기 로그인·결과 확인·구매는 한 번에 하나씩 실행합니다. 여러 인스턴스 간 중복 실행을 막으려면 분산 락이나 리더 선출이 필요합니다.
- **시도 감시**: 한 번의 시도가 15분을 넘기면 중단 신호를 보내 결제 직전이면 멈추고, 다음 시도로 넘깁니다.
- **정상 종료**: 종료 시 새 시도를 막고 진행 중인 시도를 최대 60초 기다립니다. 진행 중이던 작업 상태는 메모리에만 있어 재시작하면 사라집니다.
- **실패 진단 자료**: Playwright trace는 **로그인 이후부터** DOM 스냅샷만 기록합니다(비밀번호 입력이 남지 않음). 실패 시 `lotto-trace-*.zip`으로 저장해 메일에 첨부하고, 로그인 전 실패는 화면 캡처(PNG)를 첨부합니다.
- **알림 유실 방지**: 첨부가 5MB를 넘거나 메일 서버가 크기 초과(552)로 거부하면 첨부 없이 보냅니다. 접속 실패 같은 일시 장애는 메모리 대기열에 넣어 10분마다 48시간까지 다시 보냅니다(재시작 시 대기열은 사라짐). 실패는 `lotto.notification.failed`, 대기열 크기는 `lotto.notification.outbox.size` 메트릭으로 볼 수 있습니다.

## 구조

루트 프로젝트는 Gradle 모듈과 공통 빌드 설정을 관리합니다. Spring Boot 앱은 `boot` 모듈에 있으며, 저장소 루트에서 `./gradlew bootRun`, `./gradlew bootJar`, `./gradlew bootBuildImage`를 실행할 수 있습니다.

| 모듈 | 내용 |
|------|------|
| `boot` | 앱 진입점, Spring 빈 구성, 설정 파일, 전체 모듈을 연결하는 컨텍스트 테스트 |
| `domain` | 순수 Kotlin 도메인: 판매 달력·회차, 원장 계산, 타입·예외 |
| `application` | 포트(inbound/outbound)와 서비스(구매 절차, 작업 실행, 알림 생성) |
| `adapter-api` | 수동 실행 API |
| `adapter-scheduler` | 크론 스케줄, 기동 초기화 |
| `adapter-browser` | Playwright 자동화 |
| `adapter-mail` | 메일 발송·재발송 대기열 |

자세한 구조는 [docs/clean-architecture.md](docs/clean-architecture.md)를 참고하세요.

## Config

- `AutomationProperties`(`@ConfigurationProperties`)는 기동 시 `lotto.*` 설정을 바인딩합니다.
- 스케줄러는 `@Scheduled`로 동작하며, `lotto.cron.*` 값은(시간대 Asia/Seoul 고정) 애플리케이션 기동 시점에 적용됩니다.
