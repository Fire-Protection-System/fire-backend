FROM eclipse-temurin:21-jdk-jammy AS builder
ARG APP_VERSION=dev
WORKDIR /app

RUN apt-get update \
	&& apt-get install -y --no-install-recommends dos2unix ca-certificates \
	&& rm -rf /var/lib/apt/lists/*

COPY gradlew gradlew
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./

RUN dos2unix gradlew && chmod +x gradlew

RUN ./gradlew --no-daemon dependencies || true

COPY . /app
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy
ARG APP_VERSION=dev
LABEL org.opencontainers.image.version="${APP_VERSION}"
WORKDIR /app

RUN groupadd -g 1000 appuser || true \
	&& useradd -u 1000 -g appuser -m -s /usr/sbin/nologin appuser || true

COPY --from=builder /app/build/libs/*.jar app.jar
RUN chown appuser:appuser /app/app.jar

USER appuser
EXPOSE 8181
ENTRYPOINT ["java", "-jar", "app.jar"]
