FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache dumb-init && \
    addgroup -S spring && adduser -S spring -G spring

WORKDIR /app
COPY --chown=spring:spring /target/server*.jar app.jar

USER spring:spring
EXPOSE 8080

ENTRYPOINT ["dumb-init", "java", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseContainerSupport", \
    "-jar", "app.jar"]
