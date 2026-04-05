# ===================
# BUILD STAGE
# ===================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Installera curl för hälsokontroller
RUN apk add --no-cache curl

# Kopiera Gradle-filer först (cache)
COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts gradle.properties ./
RUN chmod +x gradlew

# Ladda ner beroenden (cachas om build.gradle.kts inte ändras)
RUN ./gradlew dependencies --no-daemon || true

# Kopiera källkod
COPY src src

# Bygg applikationen
RUN ./gradlew bootJar --no-daemon -x test

# ===================
# DEVELOPMENT STAGE
# ===================
FROM eclipse-temurin:21-jdk AS development

WORKDIR /app

# Installera verktyg för utveckling
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# Kopiera Gradle
COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts gradle.properties ./
RUN chmod +x gradlew

# Ladda ner beroenden
RUN ./gradlew dependencies --no-daemon || true

# Kopiera källkod (kommer att mountas i docker-compose)
COPY src src

# Exponera portar
EXPOSE 8080 5005

# Kör med --no-daemon för stabilitet på ARM
ENV SPRING_DEVTOOLS_RESTART_ENABLED=true
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false"

CMD ["./gradlew", "bootRun", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx1g", "--args=--spring.profiles.active=dev"]

# ===================
# PRODUCTION STAGE
# ===================
FROM eclipse-temurin:21-jre-alpine AS production

WORKDIR /app

# Säkerhet: icke-root användare
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup && \
    apk add --no-cache curl

# Kopiera JAR från builder
COPY --from=builder /app/build/libs/*.jar app.jar

# Ägarskap
RUN chown -R appuser:appgroup /app
USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

# JVM-optimeringar för containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC --enable-preview"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
