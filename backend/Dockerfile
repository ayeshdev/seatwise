FROM gradle:9.5.1-jdk25 AS build
WORKDIR /workspace

# Build files first so the dependency layer is cached until they change.
COPY build.gradle settings.gradle gradle.properties ./
RUN gradle dependencies --no-daemon > /dev/null

COPY src ./src
RUN gradle bootJar -x test --no-daemon

FROM eclipse-temurin:25-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system seatwise \
    && useradd --system --gid seatwise --home-dir /app seatwise

WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar /app/app.jar

# Surfaced by /actuator/info so the pipeline can prove which build is running.
ARG GIT_SHA=local
ENV SEATWISE_GIT_SHA=$GIT_SHA

USER seatwise
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
