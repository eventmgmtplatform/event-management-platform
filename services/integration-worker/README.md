# integration-worker

Integration worker responsible for consuming integration commands and invoking external systems.

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
docker build -t event-management/integration-worker:local .
```

The Dockerfile compiles the service in its build stage and copies only the
Quarkus runtime layout into the final image.

## Docker run

```bash
docker run --rm \
  --name integration-worker \
  -p 8083:8083 \
  event-management/integration-worker:local
```

Runtime characteristics:

- Runtime: Eclipse Temurin Java 21 JRE
- Container user: `eventmanagement`
- UID/GID: `1001`
- Working directory: `/deployments`
- Application port: `8083`
- Entrypoint: `java ${JAVA_OPTS} -jar /deployments/quarkus-run.jar`

Additional JVM options can be supplied with:

```bash
docker run --rm \
  -e JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Dexample=value" \
  -p 8083:8083 \
  event-management/integration-worker:local
```

## Runtime verification

Primary verification endpoint:

```text
http://localhost:8083/health/live
```

Example:

```bash
curl --fail --show-error http://localhost:8083/health/live
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
