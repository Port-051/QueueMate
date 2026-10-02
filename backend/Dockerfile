# syntax=docker/dockerfile:1
# notification 이미지 — 빌드는 JDK, 실행은 JRE 로 나눠 실행 이미지를 작게 둔다. 두 베이스 모두 amd64 · arm64 멀티아치다.
# 운영(Graviton ARM)용은 CI 에서: docker buildx build --platform linux/arm64 -t <레지스트리>/qm-notification:<태그> .

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 빌드 스크립트만 먼저 넣는다 — 소스가 바뀌어도 의존성 레이어가 그대로 산다
COPY gradlew settings.gradle build.gradle ./
COPY gradle/ gradle/
# 이 브랜치는 gradlew 를 실행 비트 없이(100644) 커밋했다
RUN chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle ./gradlew dependencies --no-daemon -q > /dev/null

COPY src/ src/
# 테스트는 Redis 가 있어야 돌아 이미지 빌드에서는 뺀다
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon -x test

# bootJar 는 실행 jar 와 -plain.jar 둘을 만든다 — 실행 jar 하나만 고른다(build.gradle 은 건드리지 않는다)
RUN set -eu; \
    jar=$(ls build/libs/*.jar | grep -v -- '-plain\.jar$'); \
    [ "$(printf '%s\n' "$jar" | wc -l)" -eq 1 ]; \
    cp "$jar" app.jar

# 의존성 · 로더 · 스냅숏 의존성 · 앱 코드로 레이어를 나눈다 — 코드만 바뀌면 마지막 레이어만 새로 받는다
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted


FROM eclipse-temurin:21-jre

# Ubuntu 베이스에는 uid 1000 인 ubuntu 사용자가 이미 있다 — 지우고 같은 번호로 app 을 만든다
RUN if id -u ubuntu > /dev/null 2>&1; then userdel --remove ubuntu; fi \
 && groupadd --gid 1000 app \
 && useradd --uid 1000 --gid app --no-create-home --shell /usr/sbin/nologin app

WORKDIR /app
# 자주 바뀌지 않는 것부터 쌓는다
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER 1000:1000
EXPOSE 8081
# 힙을 컨테이너 메모리 한도에 맞춘다(Fargate 태스크 메모리의 75%)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
# JWT 공개 키는 이미지에 넣지 않는다 — JWT_PUBLIC_KEY(운영) 또는 JWT_PUBLIC_KEY_FILE 로 준다. 없으면 기동하지 않는다
# HEALTHCHECK 는 두지 않는다 — JRE 이미지에 curl 이 없고, ECS 는 태스크 정의의 헬스 체크(/health/live)를 쓴다
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
