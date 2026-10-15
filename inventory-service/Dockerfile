# ── Build Stage ──
FROM eclipse-temurin:17-jdk-alpine AS builder

ARG GIT_SHA=dev
ARG BUILD_TIME=unknown
ARG VERSION=0.0.0-dev

WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

COPY src ./src
RUN ./mvnw package -DskipTests -B

# ── Runtime Stage ──
FROM eclipse-temurin:17-jre-alpine

ARG GIT_SHA=dev
ARG BUILD_TIME=unknown
ARG VERSION=0.0.0-dev

LABEL org.opencontainers.image.title="SHBS Inventory Service" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${GIT_SHA}" \
      org.opencontainers.image.created="${BUILD_TIME}" \
      org.opencontainers.image.source="https://github.com/ritik-luck/SHBS"

WORKDIR /app

# Run as non-root
RUN addgroup -S spring && adduser -S spring -G spring

# wget for healthcheck (curl not in alpine-jre)
RUN apk add --no-cache wget

USER spring:spring

COPY --from=builder --chown=spring:spring /app/target/*.jar app.jar

EXPOSE 8081

HEALTHCHECK --interval=15s --timeout=5s --retries=3 --start-period=30s \
    CMD wget --spider -q http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
