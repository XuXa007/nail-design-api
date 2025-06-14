FROM openjdk:17-jdk-slim
WORKDIR /app

COPY build/libs/nail-design-api-0.0.1-SNAPSHOT.jar app.jar

RUN mkdir -p /app/uploads
COPY uploads/ /app/uploads/

EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar","--spring.profiles.active=default"]
