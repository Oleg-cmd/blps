# Этап 1: Сборка Maven-проекта
FROM arm64v8/maven:3.9-eclipse-temurin-17 AS builder

# 1. Сборка common-dto
WORKDIR /app/common-dto
COPY common-dto/pom.xml ./pom.xml
COPY common-dto/src ./src
RUN mvn -B clean install -DskipTests

# 2. Сборка account-service
WORKDIR /app/account-service
COPY account-service/pom.xml ./pom.xml
COPY account-service/src ./src
RUN mvn -B clean package -DskipTests

# Этап 2: Создание финального образа
FROM arm64v8/openjdk:17-jdk-slim AS final

WORKDIR /app

COPY --from=builder /app/account-service/target/account-service-*.jar app.jar

EXPOSE 8081

CMD ["java", "-jar", "app.jar"]