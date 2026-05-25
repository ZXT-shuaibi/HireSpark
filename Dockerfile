FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml .
COPY bootstrap/pom.xml bootstrap/pom.xml
COPY framework/pom.xml framework/pom.xml
COPY infra-ai/pom.xml infra-ai/pom.xml
COPY mcp-server/pom.xml mcp-server/pom.xml
RUN mvn -B -pl bootstrap -am -DskipTests dependency:go-offline

COPY . .
RUN mvn -B -pl bootstrap -am -DskipTests package

FROM eclipse-temurin:17-jre-jammy AS runtime

WORKDIR /app

ENV SPRING_PROFILES_ACTIVE=""
ENV JAVA_OPTS=""

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl fonts-noto-cjk \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system ragent \
    && useradd --system --gid ragent --home-dir /app --shell /usr/sbin/nologin ragent

COPY --from=build /workspace/bootstrap/target/bootstrap-*.jar /app/ragent-bootstrap.jar

RUN chown -R ragent:ragent /app

USER ragent

EXPOSE 9090

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS http://localhost:9090/api/ragent/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/ragent-bootstrap.jar"]
