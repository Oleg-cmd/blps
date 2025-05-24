# Этап 1: Сборка Maven-проекта
FROM arm64v8/maven:3.9-eclipse-temurin-17 AS builder

# 1. Сборка common-dto (если sbp-adapter-service от него зависит)
WORKDIR /app/common-dto
COPY common-dto/pom.xml ./pom.xml
COPY common-dto/src ./src
RUN mvn -B clean install -DskipTests

# 2. Сборка sbp-adapter-service
WORKDIR /app/sbp-adapter-service
COPY sbp-adapter-service/pom.xml ./pom.xml
COPY sbp-adapter-service/src ./src
RUN mvn -B clean package -DskipTests

# Этап 2: Создание финального образа
FROM arm64v8/openjdk:17-jdk-slim AS final
WORKDIR /app
COPY --from=builder /app/sbp-adapter-service/target/sbp-adapter-service-*.jar app.jar
EXPOSE 8083
CMD ["java", "-jar", "app.jar"]