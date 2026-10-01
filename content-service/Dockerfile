# The content service image. Build from the REPO ROOT, because the service depends on
# :domain:
#
#   docker build -f content-service/Dockerfile .
#
# Railway picks this up through railway.json at the repo root.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# Leaves the Android modules out of the build: they cannot even be configured without the
# Android SDK, which this image does not have (see settings.gradle.kts).
ENV FOREWAY_SERVER_ONLY=true

# Copy only what the server needs. Nothing else from the repo — in particular never .env
# or local.properties — can end up in the image.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY domain domain
COPY tools tools
COPY content-service content-service

RUN chmod +x gradlew && ./gradlew :content-service:bootJar --no-daemon --quiet

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
USER app
WORKDIR /app
COPY --from=build /src/content-service/build/libs/content-service.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
