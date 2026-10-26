# ============================================================================
# Stage 1: Build the Spring Boot fat JAR using Maven & JDK 21
# ============================================================================
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace

COPY pom.xml ./
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# ============================================================================
# Stage 2: Minimal hardened production runtime image (JRE 21 Alpine)
# ============================================================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user for security
RUN addgroup -S sehatflow && adduser -S sehatflow -G sehatflow

COPY --from=builder /workspace/target/clinic-queue-backend-*.jar /app/app.jar
RUN chown -R sehatflow:sehatflow /app

USER sehatflow

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
