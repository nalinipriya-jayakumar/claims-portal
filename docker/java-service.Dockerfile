# syntax=docker/dockerfile:1
#
# One image definition for both Spring Boot services; MODULE picks which one.
# Build context is the repository root, because a module needs the parent POM and event-contracts.
#
#   docker build -f docker/java-service.Dockerfile --build-arg MODULE=claims-api -t claims-api .

ARG MODULE

# --- Build: compile and package with Maven (tests run in CI, not in the image build) ---
FROM maven:3.9-eclipse-temurin-21-alpine AS build
ARG MODULE
WORKDIR /workspace

# Every module POM is needed because the parent lists them all. The BuildKit cache mount keeps
# downloaded dependencies between builds, so only the first build fetches them.
COPY pom.xml ./
COPY event-contracts/pom.xml event-contracts/
COPY claims-api/pom.xml claims-api/
COPY notification-service/pom.xml notification-service/
COPY event-contracts/src event-contracts/src
COPY ${MODULE}/src ${MODULE}/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -q -pl ${MODULE} -am package -DskipTests

# Split the fat jar into layers: dependencies change rarely, application classes often.
RUN java -Djarmode=tools -jar ${MODULE}/target/${MODULE}-*.jar extract --layers --launcher --destination /extracted

# --- Runtime: JRE only, non-root ---
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app -H -s /sbin/nologin app
WORKDIR /app

COPY --from=build --chown=app:app /extracted/dependencies/ ./
COPY --from=build --chown=app:app /extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /extracted/application/ ./

USER app
# Size the heap from the container's memory limit; structured logs come from the docker profile.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE=docker
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
