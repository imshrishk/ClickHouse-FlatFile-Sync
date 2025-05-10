FROM eclipse-temurin:24-jdk AS build
WORKDIR /app

# Make mvnw executable
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# Configure Maven settings for better reliability
RUN mkdir -p /root/.m2 && \
    echo '<?xml version="1.0" encoding="UTF-8"?>' > /root/.m2/settings.xml && \
    echo '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"' >> /root/.m2/settings.xml && \
    echo '          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"' >> /root/.m2/settings.xml && \
    echo '          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0' >> /root/.m2/settings.xml && \
    echo '                          https://maven.apache.org/xsd/settings-1.0.0.xsd">' >> /root/.m2/settings.xml && \
    echo '    <mirrors>' >> /root/.m2/settings.xml && \
    echo '        <mirror>' >> /root/.m2/settings.xml && \
    echo '            <id>maven-default-http-blocker</id>' >> /root/.m2/settings.xml && \
    echo '            <mirrorOf>external:http:*</mirrorOf>' >> /root/.m2/settings.xml && \
    echo '            <name>Pseudo repository to mirror external repositories initially using HTTP.</name>' >> /root/.m2/settings.xml && \
    echo '            <url>http://0.0.0.0/</url>' >> /root/.m2/settings.xml && \
    echo '            <blocked>true</blocked>' >> /root/.m2/settings.xml && \
    echo '        </mirror>' >> /root/.m2/settings.xml && \
    echo '    </mirrors>' >> /root/.m2/settings.xml && \
    echo '    <profiles>' >> /root/.m2/settings.xml && \
    echo '        <profile>' >> /root/.m2/settings.xml && \
    echo '            <id>maven-central</id>' >> /root/.m2/settings.xml && \
    echo '            <repositories>' >> /root/.m2/settings.xml && \
    echo '                <repository>' >> /root/.m2/settings.xml && \
    echo '                    <id>central</id>' >> /root/.m2/settings.xml && \
    echo '                    <url>https://repo1.maven.org/maven2</url>' >> /root/.m2/settings.xml && \
    echo '                    <releases><enabled>true</enabled></releases>' >> /root/.m2/settings.xml && \
    echo '                    <snapshots><enabled>false</enabled></snapshots>' >> /root/.m2/settings.xml && \
    echo '                </repository>' >> /root/.m2/settings.xml && \
    echo '            </repositories>' >> /root/.m2/settings.xml && \
    echo '        </profile>' >> /root/.m2/settings.xml && \
    echo '    </profiles>' >> /root/.m2/settings.xml && \
    echo '    <activeProfiles>' >> /root/.m2/settings.xml && \
    echo '        <activeProfile>maven-central</activeProfile>' >> /root/.m2/settings.xml && \
    echo '    </activeProfiles>' >> /root/.m2/settings.xml && \
    echo '</settings>' >> /root/.m2/settings.xml

# Download dependencies with retry logic and increased timeouts
RUN ./mvnw dependency:go-offline -B \
    -Dmaven.wagon.http.retryHandler.count=5 \
    -Dmaven.wagon.http.readTimeout=180000 \
    -Dmaven.wagon.http.connectionTimeout=180000 \
    -Dmaven.wagon.http.pool=false \
    -Dmaven.wagon.http.maxRedirects=5 \
    -Dmaven.wagon.http.retryHandler.retryInterval=10000

# Copy source code
COPY src ./src

# Build the application with retry logic and increased timeouts
RUN ./mvnw package -Dmaven.test.skip=true \
    -Dmaven.wagon.http.retryHandler.count=5 \
    -Dmaven.wagon.http.readTimeout=180000 \
    -Dmaven.wagon.http.connectionTimeout=180000 \
    -Dmaven.wagon.http.pool=false \
    -Dmaven.wagon.http.maxRedirects=5 \
    -Dmaven.wagon.http.retryHandler.retryInterval=10000

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

# Set entrypoint with configurable JVM options
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /app/app.jar"]
