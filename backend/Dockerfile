# ── Build Stage ──
FROM eclipse-temurin:17-jdk-jammy AS build

ARG GIT_SHA=dev
ARG BUILD_TIME=unknown
ARG VERSION=0.0.0-dev

WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B
COPY src/ src/
RUN ./mvnw package -DskipTests -B

# ── Runtime Stage ──
FROM eclipse-temurin:17-jre-jammy

ARG GIT_SHA=dev
ARG BUILD_TIME=unknown
ARG VERSION=0.0.0-dev

LABEL org.opencontainers.image.title="SHBS Backend" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${GIT_SHA}" \
      org.opencontainers.image.created="${BUILD_TIME}" \
      org.opencontainers.image.source="https://github.com/ritik-luck/SHBS"

WORKDIR /app
RUN groupadd -r spring && useradd -r -g spring spring
USER spring:spring
COPY --from=build --chown=spring:spring /app/target/*.jar app.jar
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --retries=3 --start-period=30s \
    CMD curl -f http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
