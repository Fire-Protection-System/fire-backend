FROM openjdk:21-jdk-slim AS builder
WORKDIR /app

RUN apt-get update && apt-get install -y dos2unix
COPY . /app
RUN dos2unix gradlew && chmod +x ./gradlew
RUN ./gradlew bootJar --no-daemon

FROM openjdk:21-slim
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
