FROM maven:3.9.9-eclipse-temurin-22 AS build

WORKDIR /app
COPY pom.xml .
COPY src ./src
COPY web ./web
RUN mvn -q -DskipTests package dependency:copy-dependencies

FROM eclipse-temurin:22-jre

WORKDIR /app
COPY --from=build /app /app

ENV EVENTRSVP_DATA_DIR=/data
ENV PORT=10000

RUN mkdir -p /data

CMD ["sh", "-c", "java -cp target/classes:target/dependency/* org.example.RenderApp"]
