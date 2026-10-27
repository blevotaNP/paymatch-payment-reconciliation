# Built from the repository root (see docker-compose.yml), because the image also ships the sample files for demo data.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY backend/pom.xml .
RUN mvn -B -q dependency:go-offline
COPY backend/src src
RUN mvn -B -q -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 paymatch
WORKDIR /app
COPY --from=build /build/target/paymatch-*.jar app.jar
COPY samples samples
USER paymatch
ENV DEMO_DATA_DIRECTORY=/app/samples \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
    CMD curl -fs http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
