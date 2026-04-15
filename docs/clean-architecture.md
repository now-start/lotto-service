# Lotto Service Clean Architecture Blueprint

히스토리 저장/조회 기능을 제외한, Playwright 자동화와 메일 발송 중심의 일반적인 Spring 기반 클린 아키텍처 구조안이다.

## Package Tree

```text
org.nowstart.lotto
├─ LottoServiceApplication                            [class, @SpringBootApplication]
├─ config
│  ├─ PlaywrightConfig                                [class, @Configuration]
│  └─ LottoProperties                                 [class, @ConfigurationProperties, @Validated]
├─ domain
│  ├─ model
│  │  ├─ LottoUser                                    [class]
│  │  ├─ LottoAccountSnapshot                         [record]
│  │  ├─ LottoResult                                  [class]
│  │  ├─ LottoExecution                               [class]
│  │  └─ NotificationMessage                          [record]
│  ├─ type
│  │  ├─ TaskMode                                     [enum]
│  │  ├─ TriggerType                                  [enum]
│  │  ├─ ExecutionStatus                              [enum]
│  │  └─ StepType                                     [enum]
│  └─ exception
│     ├─ InvalidManualUserSelectionException          [class]
│     ├─ LottoAutomationException                     [class]
│     └─ NotificationSendException                    [class]
├─ application
│  ├─ port
│  │  ├─ in
│  │  │  ├─ ExecuteLottoUseCase                       [interface]
│  │  │  └─ InitializeLottoUseCase                    [interface]
│  │  └─ out
│  │     ├─ LoadLottoUsersPort                        [interface]
│  │     ├─ LottoAutomationPort                       [interface]
│  │     └─ SendNotificationPort                      [interface]
│  ├─ dto
│  │  ├─ ExecuteLottoCommand                          [record]
│  │  ├─ InitializeLottoCommand                       [record]
│  │  └─ LottoExecutionResult                         [record]
│  └─ service
│     ├─ ExecuteLottoInteractor                       [class, @Service]
│     ├─ InitializeLottoInteractor                    [class, @Service]
│     └─ LottoNotificationFactory                     [class]
└─ adapter
   ├─ in
   │  ├─ web
   │  │  ├─ LottoManualController                     [class, @RestController]
   │  │  ├─ request
   │  │  │  └─ ExecuteLottoRequest                    [record]
   │  │  └─ response
   │  │     └─ LottoExecutionResponse                 [record]
   │  ├─ scheduler
   │  │  └─ LottoScheduleExecutor                     [class, @Component, @Scheduled]
   │  └─ startup
   │     └─ LottoInitializationRunner                 [class, @Component]
   └─ out
      ├─ browser
      │  ├─ LottoAutomationPlaywrightAdapter          [class, @Component]
      │  ├─ PlaywrightSessionManager                  [class, @Component]
      │  └─ PlaywrightTraceArchive                    [class, @Component]
      ├─ mail
      │  └─ NotificationMailAdapter                   [class, @Component]
      └─ properties
         └─ LottoUsersPropertiesAdapter               [class, @Component]
```

## Layer Overview

```mermaid
flowchart TB
  classDef boot fill:#f7f7f7,stroke:#555,color:#111;
  classDef domain fill:#eef7ee,stroke:#2e7d32,color:#111;
  classDef app fill:#eef4ff,stroke:#1565c0,color:#111;
  classDef inadapter fill:#fff7e6,stroke:#ef6c00,color:#111;
  classDef outadapter fill:#fff1f2,stroke:#c62828,color:#111;

  subgraph ROOT["org.nowstart.lotto"]
    APP["LottoServiceApplication<br/>class<br/>@SpringBootApplication"]:::boot

    subgraph CFG["config"]
      PC["PlaywrightConfig<br/>class<br/>@Configuration"]:::boot
      LP["LottoProperties<br/>class<br/>@ConfigurationProperties<br/>@Validated"]:::boot
    end

    subgraph DOM["domain"]
      subgraph DMM["model"]
        DU["LottoUser<br/>class"]:::domain
        DAS["LottoAccountSnapshot<br/>record"]:::domain
        DR["LottoResult<br/>class"]:::domain
        DE["LottoExecution<br/>class"]:::domain
        NM["NotificationMessage<br/>record"]:::domain
      end
      subgraph DTP["type"]
        TM["TaskMode<br/>enum"]:::domain
        TT["TriggerType<br/>enum"]:::domain
        ES["ExecutionStatus<br/>enum"]:::domain
        ST["StepType<br/>enum"]:::domain
      end
      subgraph DEX["exception"]
        IMU["InvalidManualUserSelectionException<br/>class"]:::domain
        LAE["LottoAutomationException<br/>class"]:::domain
        NSE["NotificationSendException<br/>class"]:::domain
      end
    end

    subgraph APPP["application"]
      subgraph PIN["port.in"]
        EXIN["ExecuteLottoUseCase<br/>interface"]:::app
        INITIN["InitializeLottoUseCase<br/>interface"]:::app
      end
      subgraph POUT["port.out"]
        USERPORT["LoadLottoUsersPort<br/>interface"]:::app
        AUTOPORT["LottoAutomationPort<br/>interface"]:::app
        NOTIPORT["SendNotificationPort<br/>interface"]:::app
      end
      subgraph DTO["dto"]
        CMD["ExecuteLottoCommand<br/>record"]:::app
        ICMD["InitializeLottoCommand<br/>record"]:::app
        RES["LottoExecutionResult<br/>record"]:::app
      end
      subgraph SVC["service"]
        EXSVC["ExecuteLottoInteractor<br/>class<br/>@Service"]:::app
        INITSVC["InitializeLottoInteractor<br/>class<br/>@Service"]:::app
        NPOL["LottoNotificationFactory<br/>class"]:::app
      end
    end

    subgraph ADIN["adapter.in"]
      subgraph WEB["web"]
        CTRL["LottoManualController<br/>class<br/>@RestController"]:::inadapter
        REQ["ExecuteLottoRequest<br/>record"]:::inadapter
        RSP["LottoExecutionResponse<br/>record"]:::inadapter
      end
      subgraph SCH["scheduler"]
        SCHED["LottoScheduleExecutor<br/>class<br/>@Component<br/>@Scheduled"]:::inadapter
      end
      subgraph STU["startup"]
        RUNNER["LottoInitializationRunner<br/>class<br/>@Component"]:::inadapter
      end
    end

    subgraph ADOUT["adapter.out"]
      subgraph BROWSER["browser"]
        AUTOAD["LottoAutomationPlaywrightAdapter<br/>class<br/>@Component"]:::outadapter
        SESS["PlaywrightSessionManager<br/>class<br/>@Component"]:::outadapter
        TRACE["PlaywrightTraceArchive<br/>class<br/>@Component"]:::outadapter
      end
      subgraph MAIL["mail"]
        MAILAD["NotificationMailAdapter<br/>class<br/>@Component"]:::outadapter
      end
      subgraph PROP["properties"]
        USERAD["LottoUsersPropertiesAdapter<br/>class<br/>@Component"]:::outadapter
      end
    end
  end
```

## Dependency Diagram

```mermaid
flowchart LR
  classDef inbound fill:#fff7e6,stroke:#ef6c00,color:#111;
  classDef app fill:#eef4ff,stroke:#1565c0,color:#111;
  classDef outbound fill:#fff1f2,stroke:#c62828,color:#111;
  classDef infra fill:#f7f7f7,stroke:#555,color:#111;

  subgraph INBOUND["Inbound Adapters"]
    direction TB
    CTRL["adapter.in.web.LottoManualController<br/>class @RestController"]:::inbound
    SCHED["adapter.in.scheduler.LottoScheduleExecutor<br/>class @Component @Scheduled"]:::inbound
    RUNNER["adapter.in.startup.LottoInitializationRunner<br/>class @Component"]:::inbound
  end

  subgraph INPORTS["Application In Ports"]
    direction TB
    EXIN["application.port.in.ExecuteLottoUseCase<br/>interface"]:::app
    INITIN["application.port.in.InitializeLottoUseCase<br/>interface"]:::app
  end

  subgraph USECASES["Application Services / Use Cases"]
    direction TB
    EXSVC["application.service.ExecuteLottoInteractor<br/>class @Service"]:::app
    INITSVC["application.service.InitializeLottoInteractor<br/>class @Service"]:::app
    NPOL["application.service.LottoNotificationFactory<br/>class"]:::app
  end

  subgraph OUTPORTS["Application Out Ports"]
    direction TB
    USERPORT["application.port.out.LoadLottoUsersPort<br/>interface"]:::app
    AUTOPORT["application.port.out.LottoAutomationPort<br/>interface"]:::app
    NOTIPORT["application.port.out.SendNotificationPort<br/>interface"]:::app
  end

  subgraph OUTBOUND["Outbound Adapters"]
    direction TB
    USERAD["adapter.out.properties.LottoUsersPropertiesAdapter<br/>class @Component"]:::outbound
    AUTOAD["adapter.out.browser.LottoAutomationPlaywrightAdapter<br/>class @Component"]:::outbound
    MAILAD["adapter.out.mail.NotificationMailAdapter<br/>class @Component"]:::outbound
  end

  subgraph EXTERNAL["External Systems / Framework Objects"]
    direction TB
    LP["config.LottoProperties<br/>class @ConfigurationProperties"]:::infra
    PSM["adapter.out.browser.PlaywrightSessionManager<br/>class @Component"]:::infra
    PTA["adapter.out.browser.PlaywrightTraceArchive<br/>class @Component"]:::infra
    PW["Playwright Browser API"]:::infra
    MAIL["JavaMailSender"]:::infra
  end

  CTRL -->|calls| EXIN
  SCHED -->|calls| EXIN
  RUNNER -->|calls| INITIN

  EXSVC -. implements .-> EXIN
  INITSVC -. implements .-> INITIN

  EXSVC -->|load users| USERPORT
  EXSVC -->|automation| AUTOPORT
  EXSVC -->|create notification message| NPOL
  EXSVC -->|send result/failure| NOTIPORT

  INITSVC -->|load users| USERPORT
  INITSVC -->|login smoke check| AUTOPORT
  INITSVC -->|create init message| NPOL
  INITSVC -->|send init notification| NOTIPORT

  USERAD -. implements .-> USERPORT
  AUTOAD -. implements .-> AUTOPORT
  MAILAD -. implements .-> NOTIPORT

  USERAD -->|read config users| LP
  AUTOAD -->|create/close session| PSM
  AUTOAD -->|archive trace| PTA
  AUTOAD -->|browser automation| PW
  MAILAD -->|send mail| MAIL
```

## Annotation Rules

| Package | Main Types | Spring / JPA Annotation Rule |
| --- | --- | --- |
| `org.nowstart.lotto.config` | config class | `@Configuration`, `@Bean`, `@ConfigurationProperties`, `@Validated` |
| `org.nowstart.lotto.domain.*` | model, enum, exception | annotation 없음 |
| `org.nowstart.lotto.application.port.*` | interface | annotation 없음 |
| `org.nowstart.lotto.application.dto` | command/result | annotation 없음 |
| `org.nowstart.lotto.application.service` | use case class | `@Service` 만 허용, `@Transactional` 없음 |
| `org.nowstart.lotto.adapter.in.web` | controller | `@RestController`, MVC mapping annotations |
| `org.nowstart.lotto.adapter.in.scheduler` | scheduler | `@Component`, `@Scheduled` |
| `org.nowstart.lotto.adapter.in.startup` | startup runner | `@Component` |
| `org.nowstart.lotto.adapter.out.browser` | Playwright adapter | `@Component` |
| `org.nowstart.lotto.adapter.out.mail` | mail adapter | `@Component` |
| `org.nowstart.lotto.adapter.out.properties` | properties adapter | `@Component` |

## Transaction Boundary

- 현재 구조에는 히스토리 저장/조회가 없으므로 DB 트랜잭션 경계가 없다.
- Playwright 로그인/구매/조회와 메일 발송은 모두 외부 I/O 이므로 `@Transactional` 대상이 아니다.
- 나중에 실행 이력 저장이 추가되면 그때 별도 persistence adapter 와 record service 를 추가하고, 짧은 저장 메서드에만 `@Transactional` 을 둔다.
