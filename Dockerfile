FROM openjdk:17-jdk-slim

WORKDIR /app

ARG JAR_FILE=build/libs/nail-design-api-0.0.1-SNAPSHOT.jar

COPY ${JAR_FILE} app.jar

RUN mkdir -p /app/uploads

COPY src/main/resources/test-images/* /app/uploads/

EXPOSE 8080

ENTRYPOINT ["java","-jar","app.jar"]