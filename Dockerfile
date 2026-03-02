FROM gradle:8.14.3-jdk25 AS build
WORKDIR /app
COPY . .
RUN gradle :runner-service:installDist --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/runner-service/build/install/runner-service ./runner-service
EXPOSE 8080
ENTRYPOINT ["/app/runner-service/bin/runner-service"]
