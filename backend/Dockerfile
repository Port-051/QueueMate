# 빌드 단계. 이 저장소는 gradle wrapper를 두지 않으므로 gradle 이미지를 그대로 쓴다.
FROM gradle:8-jdk21 AS build
WORKDIR /workspace

# 의존성 정의를 먼저 복사해 소스만 바뀐 재빌드에서 레이어 캐시를 재사용한다.
COPY settings.gradle build.gradle ./
RUN gradle --no-daemon dependencies --quiet || true

COPY src ./src
RUN gradle --no-daemon bootJar

# 실행 단계. JDK가 아닌 JRE만 담아 이미지와 공격 표면을 줄인다.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# root로 돌리지 않는다.
RUN addgroup -S queuemate && adduser -S queuemate -G queuemate
# 업로드된 프로필 사진이 쌓이는 곳. 볼륨을 붙이기 전에 이미지 안에 만들어 두어야
# docker가 새 볼륨을 이 소유권으로 초기화한다. 없으면 root 소유로 생기고 앱이 못 쓴다.
RUN mkdir -p /var/lib/queuemate/avatars && chown -R queuemate:queuemate /var/lib/queuemate
COPY --from=build /workspace/build/libs/*.jar app.jar
USER queuemate

EXPOSE 8080

# 컨테이너 메모리 한도를 힙에 반영한다. 고정 -Xmx는 한도를 바꿀 때마다 따라 고쳐야 한다.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
