# --- Этап 1: Сборка Maven-проекта ---
FROM arm64v8/maven:3.9-eclipse-temurin-17 AS builder

RUN echo "Building on $(uname -m)"

# Сначала соберем common-dto
WORKDIR /app/common-dto
COPY common-dto/pom.xml ./pom.xml
COPY common-dto/src ./src
RUN mvn -B clean install -DskipTests

# Теперь собираем notification-service
WORKDIR /app/notification-service
COPY notification-service/pom.xml ./pom.xml
COPY notification-service/src ./src
RUN mvn -B clean package -DskipTests

# --- Этап 2: Создание финального образа ---
FROM arm64v8/openjdk:17-jdk-slim AS final

WORKDIR /app
RUN echo "Running on $(uname -m)"

COPY --from=builder /app/notification-service/target/notification-service-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8082

CMD ["java", "-jar", "app.jar"]