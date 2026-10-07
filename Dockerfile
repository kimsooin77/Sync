FROM node:24-alpine AS frontend
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm test && npm run build

FROM eclipse-temurin:21-jdk AS application-build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle/ gradle/
COPY src/main/ src/main/
RUN chmod +x gradlew
COPY --from=frontend /workspace/frontend/dist /workspace/frontend/dist
RUN ./gradlew --no-daemon -PfrontendDistDir=/workspace/frontend/dist -x test bootJar

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=application-build --chown=app:app /workspace/build/libs/*.jar /app/application.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
