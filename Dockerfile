# base image 
FROM openjdk:21-jdk-slim

# set working directory 
WORKDIR /app

# copy app code
COPY . /app

# make gradlew executable 
RUN chmod +x ./gradlew

# set environment variable for Spring
ENV SPRING_PROFILES_ACTIVE=dev

# start app
CMD ["./gradlew", "bootRun"]