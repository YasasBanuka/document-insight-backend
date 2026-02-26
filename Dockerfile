# ==========================================
# STAGE 1: Build the Application
# ==========================================
# We use a heavy Maven image just to build the code. We name this stage 'builder'.
FROM maven:3.9.6-eclipse-temurin-21 AS builder

# Set the working directory inside the container
WORKDIR /app

# Step 1: Copy ONLY the pom.xml first
COPY pom.xml .

# Download dependencies (this caches them so Docker doesn't redownload every time you change a single line of Java code)
RUN mvn dependency:go-offline -B

# Step 2: Copy the actual source code
COPY src ./src

# Build the final .jar file, skipping tests to make the build faster
RUN mvn clean package -DskipTests

# ==========================================
# STAGE 2: Run the Application
# ==========================================
# We completely throw away the Maven image and start fresh with a tiny JRE image!
# Note: We must use 'jammy' (Ubuntu) instead of 'alpine' because spring-ai-transformers
# uses Deep Java Library (DJL) which requires glibc (Alpine uses musl and will crash).
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# We reach back into the 'builder' stage and copy ONLY the final compiled .jar file
COPY --from=builder /app/target/*.jar app.jar

# We create the manual uploads folder inside the container so the app doesn't crash when trying to save files
RUN mkdir -p /app/uploads && \
    chmod 777 /app/uploads

# Expose port 8080 so Nginx can talk to the backend
EXPOSE 8080

# The command to execute when the server turns on
ENTRYPOINT ["java", "-jar", "app.jar"]
