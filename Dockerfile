# syntax=docker/dockerfile:1.7

# --- Build: compile the executable WAR and split it into cache-friendly layers. ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src

COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp package -DskipTests \
    && java -Djarmode=tools -jar target/*.war extract --layers --launcher --destination /app

# --- Runtime: JRE only, no shell, non-root (uid 65532). ---
FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app

COPY --from=build /app/dependencies/ ./
COPY --from=build /app/spring-boot-loader/ ./
COPY --from=build /app/snapshot-dependencies/ ./
COPY --from=build /app/application/ ./

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

USER nonroot
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.WarLauncher"]
