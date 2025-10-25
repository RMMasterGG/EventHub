FROM gradle:8-jdk24 AS builder
WORKDIR /app
COPY . .
RUN gradle build -x test

FROM openjdk:24-slim
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]