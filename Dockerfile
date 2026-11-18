FROM gradle:8.6-jdk21 AS build
WORKDIR /app
ENV JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true"
COPY . .
RUN gradle --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
