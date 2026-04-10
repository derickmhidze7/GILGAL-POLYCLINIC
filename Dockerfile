# Stage 1: Build
FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /app

COPY hospital/pom.xml .
COPY hospital/src ./src
RUN mvn dependency:go-offline -B && mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache bash \
  && addgroup -S spring \
  && adduser -S -D -h /home/spring -s /sbin/nologin -G spring spring \
  && mkdir -p /home/spring \
  && chown -R spring:spring /home/spring

WORKDIR /app
COPY --from=build --chown=spring:spring /app/target/hospital-*.jar app.jar

USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx512m", "-Xms256m", "-jar", "app.jar"]