# ---------- Stage 1: build the JAR ----------
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

# Download dependencies first, so Docker caches them when only source code changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests run locally / in CI; skip them here to keep the image build fast
RUN mvn -B -q -DskipTests package

# ---------- Stage 2: small runtime image (JRE only, no Maven, no source code) ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Don't run as root inside the container
RUN useradd --system --no-create-home appuser
COPY --from=build /app/target/securebank-backend-1.0.0.jar app.jar
USER appuser

EXPOSE 8080
# Fit comfortably in a 512 MB free-tier instance
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k"
ENTRYPOINT ["java", "-jar", "app.jar"]
