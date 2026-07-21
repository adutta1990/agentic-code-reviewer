# syntax=docker/dockerfile:1

# ---- build stage ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /build
# Cache dependencies on the pom first, then build.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q -DskipTests dependency:go-offline || true
COPY src/ src/
RUN ./mvnw -B -q -DskipTests package

# ---- runtime stage ----
FROM eclipse-temurin:17-jdk AS runtime
# The sandbox shells out to git and Maven and COMPILES Java (surefire forks javac), so the runtime
# image needs a full JDK plus git and maven — not a slim JRE. Target repos usually carry their own
# ./mvnw; the maven package here is only the fallback.
RUN apt-get update \
    && apt-get install -y --no-install-recommends git maven ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /build/target/*.war app.war

# Run unprivileged; give the sandbox a writable work root for its temp worktrees.
RUN useradd -r -u 1001 appuser \
    && mkdir -p /work \
    && chown appuser:appuser /work /app
USER appuser
ENV SANDBOX_WORK_ROOT=/work \
    JAVA_OPTS=""

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.war"]
