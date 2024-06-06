# lotto

[![Build](https://github.com/now-start/lotto-service/actions/workflows/deploy.yaml/badge.svg)](https://github.com/now-start/lotto-service/actions/workflows/deploy.yaml)
[![Lotto](https://github.com/now-start/lotto-service/actions/workflows/lotto.yaml/badge.svg)](https://github.com/now-start/lotto-service/actions/workflows/lotto.yaml)

로또 자동 구매

## Docker

docker-compose

```yaml
version: '3.8'
services:
  lotto-app:
    image: ghcr.io/now-start/lotto-app:latest
    environment:
      - LOTTO_ID=your_lotto_id
      - LOTTO_PASSWORD=your_lotto_password
      - GITHUB_TOKEN=your_github_token
      - GITHUB_REPOSITORY=your_github_repository
```

## Notes
 - `LOTTO_ID` : 로또 사이트 아이디
 - `LOTTO_PASSWORD` : 로또 사이트 비밀번호
 - `GITHUB_TOKEN` : 깃허브 토큰
 - `GITHUB_REPOSITORY` : 깃허브 저장소 (now-start/lotto-service)