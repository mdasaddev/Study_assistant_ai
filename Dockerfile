FROM eclipse-temurin:21-jdk-jammy
COPY target/*.jar study_app.jar
ENTRYPOINT ["java","-jar","/study_app.jar"]