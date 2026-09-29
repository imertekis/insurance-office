# Task 31: the application's image, for the office server (deploy/compose.yaml,
# docs/DEPLOYMENT.md). It holds the jar that ./mvnw verify built and tested
# on the development machine, so the server runs exactly what was checked
# (TASKS, Ε6); nothing is compiled or tested here:
#
#   ./mvnw verify
#   docker buildx build --platform linux/amd64 --build-arg APP_VERSION=<version> \
#     -t insurance-office:<version> --load .
#
# That is the last stage, "server", which a build without --target makes.
# There is no RUN step in it, so an arm64 image (a Raspberry Pi, Ε5) builds
# without emulation; only running one on an amd64 machine needs QEMU.
#
# Task 39b: the demo (compose.demo.yaml) builds the stage "demo" instead, for
# a machine with only Docker: its jar comes from the stage "build", made with
# the Maven wrapper, without tests. BuildKit makes only the stages a target
# needs, so building the server's image never compiles anything.

# The demo's jar, from the sources.
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /build
COPY mvnw pom.xml ./
COPY .mvn/wrapper/maven-wrapper.properties .mvn/wrapper/
COPY src/main src/main
# Maven and the dependencies stay in BuildKit's cache between builds. sh, as
# a clone on Windows may lose the script's executable bit.
RUN --mount=type=cache,target=/root/.m2 \
    sh mvnw --batch-mode --no-transfer-progress -DskipTests package

# What both images run.
FROM eclipse-temurin:21-jre-noble AS runtime

# The office's clock. The JVM's zone is "today" in the services
# (LocalDate.now(): the home page, the policy in force) and the time of every
# created_at; the JDBC driver gives it to the database session too, for the
# now() of audit_log. A container is in UTC unless told otherwise.
ENV TZ=Europe/Athens

WORKDIR /app

# Not root: the unprivileged user every Ubuntu image has. Tomcat and POI
# write only to /tmp.
USER nobody

EXPOSE 8080
# The heap follows the container's memory limit (APP_MEMORY in deploy/.env).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

# Task 39b: the demo's image. compose.demo.yaml gives it the profile.
FROM runtime AS demo
COPY --from=build /build/target/insurance-office-*.jar app.jar

# Task 31: the office server's image, the default.
FROM runtime AS server
ARG APP_VERSION=dev
LABEL org.opencontainers.image.title="insurance-office" \
      org.opencontainers.image.version="${APP_VERSION}"
COPY target/insurance-office-*.jar app.jar
