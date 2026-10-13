FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --home /app app && mkdir -p /app/storage && chown app /app/storage
COPY --from=build /app/target/backend-*.jar app.jar
USER app
ENV SPRING_PROFILES_ACTIVE=prod \
    STORAGE_DIR=/app/storage
EXPOSE 8088
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.awt.headless=true", "-jar", "app.jar"]
