# Stage 1: Build JAR
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Stage 2: Run app
FROM eclipse-temurin:21-jdk-jammy
COPY --from=build /app/target/*.jar study_app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/study_app.jar"]