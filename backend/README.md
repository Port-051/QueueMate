# Backend

Java 21 + Spring Boot modular monolith.

## Local
```bash
docker compose up -d postgres redis
cd backend
./gradlew bootRun
```

## Package ownership
- matching/reservation/gameconfig: Member 2
- auth/user/party/social/realtime/common/infra: Member 3

OpenAPI와 docs가 skeleton 코드보다 우선한다.
