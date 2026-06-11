# Multi-stage build shared by all three services: pass the module name as a
# build arg. Stage 1 builds the fat jar with the Maven wrapper, stage 2 runs
# it on a slim JRE image.
FROM eclipse-temurin:21-jdk-jammy AS build
ARG MODULE
WORKDIR /workspace

# Copy the build descriptors first so dependency downloads cache as a layer
# and only re-run when a pom changes, not on every source edit.
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
COPY common/pom.xml common/pom.xml
COPY ingest-service/pom.xml ingest-service/pom.xml
COPY detection-service/pom.xml detection-service/pom.xml
COPY alert-api-service/pom.xml alert-api-service/pom.xml
COPY tools/event-simulator/pom.xml tools/event-simulator/pom.xml
RUN ./mvnw -ntp -q dependency:go-offline || true

COPY common common
COPY ingest-service ingest-service
COPY detection-service detection-service
COPY alert-api-service alert-api-service
COPY tools tools
RUN ./mvnw -ntp -q -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:21-jre-jammy
ARG MODULE
# curl is used by the compose healthcheck against the actuator endpoint
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/${MODULE}/target/*.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
