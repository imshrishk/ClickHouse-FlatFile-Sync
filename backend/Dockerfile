FROM eclipse-temurin:24-jdk AS build
WORKDIR /app

# Copy maven wrapper and pom.xml
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Download dependencies
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN ./mvnw package -DskipTests

# Runtime image
FROM eclipse-temurin:24-jre
WORKDIR /app

# Copy the built jar file from the build stage
COPY --from=build /app/target/bidirectional-0.0.1-SNAPSHOT.jar app.jar

# Create temp directory
RUN mkdir -p /app/temp
VOLUME /app/temp

# Expose port
EXPOSE 8080

# Set entrypoint
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
