# syntax=docker/dockerfile:1
# platform 앱 이미지. 빌드는 backend/ 에서 — docker build -t qm-platform . (두 베이스 이미지 모두 멀티아치)

# ---- 빌드 ----
# 빌드 단계는 빌드하는 쪽 CPU(네이티브)에서 돈다 — Gradle 컴파일을 에뮬레이션으로 돌리면 몇 배 느리다.
# 결과물(jar · 추출한 계층)은 CPU 를 안 가리므로 실행 단계만 타깃(운영 Graviton — arm64)으로 만들어진다. CI 에서: docker buildx build --platform linux/arm64 -t qm-platform .
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 빌드 스크립트만 먼저 넣는다 — src 만 바뀐 빌드는 의존성 받기를 다시 하지 않는다
COPY gradlew settings.gradle build.gradle ./
COPY gradle/ gradle/
# 이 브랜치는 실행 비트가 있지만 다른 두 앱은 없이 커밋돼 있다 — 세 앱이 같은 모양이도록 붙인다
RUN chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle ./gradlew dependencies --no-daemon -q > /dev/null

COPY src/ src/
# 테스트는 PostgreSQL · Redis 가 있어야 돌아 여기서는 뺀다(테스트는 따로 돌린다)
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon -x test

# bootJar 는 -plain.jar 도 만든다 — 실행 jar 하나만 고른다(build.gradle 은 그대로 둔다).
# 계층으로 풀어 두면 코드만 바뀐 배포는 application 층만 새로 받는다
RUN find build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' -exec cp {} app.jar \; \
 && test -f app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ---- 실행 ----
FROM eclipse-temurin:21-jre
# 비루트로 돈다. 베이스(Ubuntu)에 uid 1000 의 ubuntu 가 이미 있어 지우고 같은 uid 로 app 을 만든다
RUN userdel --remove ubuntu \
 && groupadd --gid 1000 app \
 && useradd --uid 1000 --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app

# 덜 바뀌는 층부터 놓는다. 파일은 root 소유라 앱은 읽기만 한다 — JWT 개발용 키를 새로 만들지 못한다.
# 키는 JWT_PRIVATE_KEY · JWT_PUBLIC_KEY(운영 — Secrets Manager)나 /app/.dev-keys 읽기 전용 마운트(로컬)로 넣는다
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER app
EXPOSE 8082
# 컨테이너 메모리 한도의 75% 를 힙으로 — 나머지는 메타스페이스 · 스레드 스택 · 다이렉트 버퍼 몫
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
# HEALTHCHECK 는 두지 않는다 — JRE 이미지에 curl 이 없고 ECS 는 태스크 정의의 healthCheck 로 /health/live · /health/ready 를 본다
# exec 형식 — SIGTERM 이 java 에 바로 가야 graceful shutdown 이 돈다
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
