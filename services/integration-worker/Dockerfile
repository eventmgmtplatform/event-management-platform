FROM eclipse-temurin:21-jre

WORKDIR /opt/integration-worker

COPY target/quarkus-app/lib/ ./lib/
COPY target/quarkus-app/*.jar ./
COPY target/quarkus-app/app/ ./app/
COPY target/quarkus-app/quarkus/ ./quarkus/

EXPOSE 8083

USER 1001

ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
