# event-state-service

State consolidation service for event lifecycle, PostgreSQL persistence and downstream publication.

## Build standard

This service follows the normalized Event Management build contract:

- Java 21
- Quarkus 3.38.0
- Maven Compiler Plugin 3.15.0
- Maven Surefire and Failsafe 3.5.6
- Multi-stage Docker build
- Non-root runtime user
- Self-contained `docker build .`

## Maven build

From the service directory:

```bash
mvn clean package
```

The Quarkus fast-jar layout is generated under:

```text
target/quarkus-app/
```

Run the packaged application locally with:

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

## Docker build

No previous Maven build is required.

```bash
docker build -t event-management/event-state-service:local .
```

The Dockerfile compiles the service in its build stage and copies only the
Quarkus runtime layout into the final image.

## Docker run

```bash
docker run --rm \
  --name event-state-service \
  -p 8084:8084 \
  event-management/event-state-service:local
```

Runtime characteristics:

- Runtime: Eclipse Temurin Java 21 JRE
- Container user: `eventmanagement`
- UID/GID: `1001`
- Working directory: `/deployments`
- Application port: `8084`
- Entrypoint: `java ${JAVA_OPTS} -jar /deployments/quarkus-run.jar`

Additional JVM options can be supplied with:

```bash
docker run --rm \
  -e JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Dexample=value" \
  -p 8084:8084 \
  event-management/event-state-service:local
```

## Runtime verification

Primary verification endpoint:

```text
http://localhost:8084/health/live
```

Example:

```bash
curl --fail --show-error http://localhost:8084/health/live
```

## Configuration

Runtime configuration must be supplied through environment variables or an
external configuration mechanism.

Secrets, credentials, tokens and passwords must not be embedded in:

- the Dockerfile;
- the container image;
- the Maven POM;
- source-controlled configuration.

## CI/CD

Cloud Build pipelines and Artifact Registry publication are outside the scope
of OS_08_12.1. They will be implemented in OS_08_12.2.

## ESS baseline and repeatable health certification

See [the service runbook](../../docs/event-state-service/README.md) for the shared
fixture, unit/JTA integration tests, `emctl event-state-service test`, controlled
local deployment and the explicit gap between current functionality and target V1.
PostgreSQL credentials must be supplied; `POSTGRES_PASSWORD` has no default.

[Historial de cambios del componente](CHANGELOG.md).
