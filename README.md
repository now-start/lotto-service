# lotto

[![Build](https://github.com/now-start/lotto-service/actions/workflows/deploy.yaml/badge.svg)](https://github.com/now-start/lotto-service/actions/workflows/deploy.yaml)
[![Lotto](https://github.com/now-start/lotto-service/actions/workflows/lotto.yaml/badge.svg)](https://github.com/now-start/lotto-service/actions/workflows/lotto.yaml)

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
```

## Environment

| Key               | Value                   | Description | Required |
|-------------------|-------------------------|-------------|----------|
| LOTTO_ID          |                         | 로또 사이트 아이디  | O        |
| LOTTO_PASSWORD    |                         | 로또 사이트 비밀번호 | O        |
| GITHUB_TOKEN      |                         | 깃허브 토큰      | X        |
| GITHUB_REPOSITORY | now-start/lotto-service | 깃허브 저장소     | X        |
| RESULT_SCHEDULE   | m h dom mon dow         | 결과 조회 스케줄   | X        |
| BUY_SCHEDULE      | m h dom mon dow         | 구매 스케줄      | X        |