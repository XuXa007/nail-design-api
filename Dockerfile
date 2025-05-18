FROM openjdk:17.0.1-jdk-slim
WORKDIR /app

# Копируем готовый JAR из host build-контекста
COPY build/libs/nail-design-api-0.0.1-SNAPSHOT.jar app.jar

# Папка для загружаемых картинок
RUN mkdir -p /app/uploads
COPY uploads/ /app/uploads/

EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar","--spring.profiles.active=default"]
