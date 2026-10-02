# syntax=docker/dockerfile:1
# app:matching 이미지. backend/ 에서 빌드한다 — docker build -t qm-matching .
# 운영은 Graviton(ARM)이다. CI 에서는 docker buildx build --platform linux/arm64 -t qm-matching . (두 베이스 이미지 모두 멀티아치)

# ---- 빌드 ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 빌드 스크립트를 먼저 넣어 src 가 바뀌어도 의존성 레이어는 그대로 쓴다.
# gradlew 는 실행 비트 없이(100644) 커밋돼 있다
COPY gradlew settings.gradle build.gradle ./
COPY gradle/ gradle/
RUN chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle ./gradlew dependencies --no-daemon -q > /dev/null

COPY src/ src/
# 테스트는 Redis 가 있어야 돌아 여기서는 뺀다(CI 의 별도 단계 몫)
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon -x test

# bootJar 는 -plain.jar 도 만든다 — 실행 jar 하나만 골라 계층별로 푼다
RUN find build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' -exec cp {} app.jar \; \
 && test -f app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ---- 실행 ----
FROM eclipse-temurin:21-jre
# 베이스(Ubuntu)의 uid 1000 은 ubuntu 다 — 지우고 같은 uid 로 app 을 만든다
RUN if id ubuntu >/dev/null 2>&1; then userdel --remove ubuntu; fi \
 && groupadd --gid 1000 app \
 && useradd --uid 1000 --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app

# 자주 바뀌는 것을 뒤에 둬 배포마다 application 레이어만 새로 받는다.
# 파일은 root 소유로 둬 앱이 자기 클래스를 고치지 못한다
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER app
EXPOSE 8080
# 컨테이너 메모리 한도의 75% 를 힙으로 — 나머지는 메타스페이스 · 스레드 스택 · 다이렉트 버퍼 몫
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
# HEALTHCHECK 는 두지 않는다 — ECS 태스크 정의 · ALB 가 /health/live 를 본다
# 필요한 환경변수 — REDIS_HOST · REDIS_PORT · JWT_PUBLIC_KEY(또는 JWT_PUBLIC_KEY_FILE). DB 는 없다
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
