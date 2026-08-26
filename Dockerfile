FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY uniconnect-core/pom.xml uniconnect-core/
COPY unicconnect-api/pom.xml unicconnect-api/
COPY uniconnect-rmi-server/pom.xml uniconnect-rmi-server/
RUN mvn -q dependency:go-offline -pl uniconnect-core,unicconnect-api,uniconnect-rmi-server -am || true
COPY uniconnect-core/src uniconnect-core/src
COPY unicconnect-api/src unicconnect-api/src
COPY uniconnect-rmi-server/src uniconnect-rmi-server/src
RUN mvn -q clean package -pl unicconnect-api -am -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/unicconnect-api/target/unicconnect-api-*.jar app.jar
EXPOSE 8080 1099
ENTRYPOINT ["java", "-jar", "app.jar"]
