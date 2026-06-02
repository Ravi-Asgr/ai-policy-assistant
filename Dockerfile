# Importing JDK and copying required files
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# Copy Maven wrapper
#COPY mvnw .
#COPY .mvn .mvn

# Set execution permission for the Maven wrapper
#RUN chmod +x ./mvnw
#RUN ./mvnw clean package -DskipTests

# Stage 2: Create the final Docker image using OpenJDK 17
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

#VOLUME /tmp

# Copy the JAR from the build stage
COPY --from=builder /app/target/*.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]
EXPOSE 8080