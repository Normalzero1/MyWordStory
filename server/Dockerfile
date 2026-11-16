FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew installDist --no-daemon

FROM eclipse-temurin:21-jre
COPY --from=build /src/build/install/server /app
# e2-micro has 1 GB of RAM shared with PostgreSQL.
ENV JAVA_OPTS="-Xmx256m"
EXPOSE 8080
CMD ["/app/bin/server"]
