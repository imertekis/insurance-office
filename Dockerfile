# Task 31: the application's image, for the office server (deploy/compose.yaml,
# docs/DEPLOYMENT.md). It holds the jar that ./mvnw verify built and tested
# on the development machine, so the server runs exactly what was checked
# (TASKS, Ε6); nothing is compiled or tested here:
#
#   ./mvnw verify
#   docker buildx build --platform linux/amd64 --build-arg APP_VERSION=<version> \
#     -t insurance-office:<version> --load .
#
# There is no RUN step, so an arm64 image (a Raspberry Pi, Ε5) builds without
# emulation; only running one on an amd64 machine needs QEMU.
FROM eclipse-temurin:21-jre-noble

ARG APP_VERSION=dev
LABEL org.opencontainers.image.title="insurance-office" \
      org.opencontainers.image.version="${APP_VERSION}"

# The office's clock. The JVM's zone is "today" in the services
# (LocalDate.now(): the home page, the policy in force) and the time of every
# created_at; the JDBC driver gives it to the database session too, for the
# now() of audit_log. A container is in UTC unless told otherwise.
ENV TZ=Europe/Athens

WORKDIR /app
COPY target/insurance-office-*.jar app.jar

# Not root: the unprivileged user every Ubuntu image has. Tomcat and POI
# write only to /tmp.
USER nobody

EXPOSE 8080
# The heap follows the container's memory limit (APP_MEMORY in deploy/.env).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
