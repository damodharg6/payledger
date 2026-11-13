# syntax=docker/dockerfile:1
# PayLedger API image. Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
#
# Stage 1 builds the jar with the full JDK; stage 2 runs it on a small JRE as a non-root user.
# Tests are not run here: they need Docker (Testcontainers) and already run in CI before any
# image is built.

# ---------- build ----------
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /src

# Dependencies first: this layer is cached until pom.xml changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package \
 && java -Djarmode=tools -jar target/payledger-*.jar extract --layers --launcher --destination /extracted

# ---------- run ----------
FROM eclipse-temurin:21-jre-noble
RUN groupadd --system payledger && useradd --system --gid payledger --uid 1001 payledger
WORKDIR /app

# Least-changing layers first, so a code change only re-ships the small "application" layer.
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./

USER payledger
EXPOSE 8080
# Use up to 75% of the container's memory limit for the heap (works on a 512 MB free tier).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
  CMD curl -fsS http://localhost:${PORT:-8080}/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
