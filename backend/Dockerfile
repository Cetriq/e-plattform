# ===================
# BUILD STAGE
# ===================
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts gradle.properties ./
RUN chmod +x gradlew

RUN ./gradlew dependencies --no-daemon || true

COPY src src

RUN ./gradlew bootJar --no-daemon -x test

# ===================
# DEVELOPMENT STAGE
# ===================
FROM eclipse-temurin:21-jdk-jammy AS development

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts gradle.properties ./
RUN chmod +x gradlew

RUN ./gradlew dependencies --no-daemon || true

COPY src src

EXPOSE 8080 5005

ENV SPRING_DEVTOOLS_RESTART_ENABLED=true
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false"

CMD ["./gradlew", "bootRun", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx1g", "--args=--spring.profiles.active=dev"]

# ===================
# PRODUCTION STAGE
# ===================
FROM eclipse-temurin:21-jre-jammy AS production

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && \
    groupadd --system --gid 1001 appgroup && \
    useradd --system --uid 1001 --gid appgroup appuser

COPY --from=builder /app/build/libs/*.jar app.jar

RUN chown -R appuser:appgroup /app
USER appuser

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC --enable-preview"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
