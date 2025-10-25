FROM gradle:8-jdk24
WORKDIR /app
COPY . .
RUN gradle build -x test
EXPOSE 8080
CMD ["java", "-jar", "build/libs/*.jar"]
