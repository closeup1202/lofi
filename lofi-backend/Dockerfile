FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY lofi-core ./lofi-core
COPY lofi-backend ./lofi-backend

# settings.gradle includes all subprojects; create stub directories so Gradle
# does not fail during settings evaluation when those sources are not needed.
RUN mkdir -p lofi-collector lofi-actuator lofi-spring-boot-starter \
             lofi-cli lofi-integration-test lofi-otel-exporter

RUN ./gradlew :lofi-backend:bootJar --no-daemon -q

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /data

COPY --from=builder /workspace/lofi-backend/build/libs/*.jar app.jar

ENV LOFI_BACKEND_DB_PATH=/data/metrics.db

EXPOSE 9292

ENTRYPOINT ["java", "-jar", "app.jar"]
