# Multi-stage build for Dosya Spring Boot API (Railway / Docker)
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw -B -q -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -B -DskipTests package \
    && cp target/dossia-*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S dossia && adduser -S dossia -G dossia
USER dossia

COPY --from=build /workspace/app.jar /app/app.jar

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
EXPOSE 8080

# Railway injects PORT; Spring reads server.port=${PORT:8080}
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
