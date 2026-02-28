FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /app
COPY pom.xml .
COPY src ./src

RUN apk add --no-cache maven && \
    mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
COPY --from=build /app/target/bpmn-plus-1.0.0.jar app.jar

EXPOSE 9997

ENTRYPOINT ["java", "-jar", "app.jar"]
