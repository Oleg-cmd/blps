# Этап 1: Сборка Maven-проекта
FROM arm64v8/maven:3.9-eclipse-temurin-17 AS builder

# 1. Сборка common-dto
WORKDIR /app/common-dto
# Копируем исходники common-dto из корня контекста сборки Docker
COPY common-dto/pom.xml ./pom.xml
COPY common-dto/src ./src
RUN mvn -B clean install -DskipTests

# 2. Сборка transfer-service
WORKDIR /app/transfer-service
# Копируем исходники transfer-service из корня контекста сборки Docker
COPY transfer-service/pom.xml ./pom.xml
COPY transfer-service/src ./src
RUN mvn -B clean package -DskipTests

# Этап 2: Создание финального образа
FROM arm64v8/openjdk:17-jdk-slim AS final

WORKDIR /app

# Копируем JAR-файл transfer-service из этапа сборки
# Убедитесь, что имя артефакта и версия верны, или используйте wildcard
COPY --from=builder /app/transfer-service/target/transfer-service-*.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]