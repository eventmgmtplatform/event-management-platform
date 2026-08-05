# ============================================================
# BUILD STAGE
# ============================================================

FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .

RUN mvn \
    -B \
    -ntp \
    dependency:go-offline

COPY src ./src

RUN mvn \
    -B \
    -ntp \
    clean package \
    -DskipTests


# ============================================================
# RUNTIME STAGE
# ============================================================

FROM eclipse-temurin:21-jre

WORKDIR /deployments

RUN groupadd \
        --gid 1001 \
        eventmanagement \
    && useradd \
        --uid 1001 \
        --gid eventmanagement \
        --shell /sbin/nologin \
        eventmanagement

COPY --from=build \
    /workspace/target/quarkus-app/lib/ \
    /deployments/lib/

COPY --from=build \
    /workspace/target/quarkus-app/*.jar \
    /deployments/

COPY --from=build \
    /workspace/target/quarkus-app/app/ \
    /deployments/app/

COPY --from=build \
    /workspace/target/quarkus-app/quarkus/ \
    /deployments/quarkus/

RUN chown -R eventmanagement:eventmanagement /deployments

USER eventmanagement

EXPOSE 8084

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /deployments/quarkus-run.jar"]
