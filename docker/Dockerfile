# Stage 1 — build (tags exactos, D1/RG-1: maven:3.9-eclipse-temurin-21 del R2)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Cache de dependencias como capa propia: tocar código en C2-C7 no re-descarga el mundo
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

# Stage 2 — runtime (temurin-21-jre del R8, tag exacto, sin build tools ni JDK)
FROM eclipse-temurin:21-jre
# curl SOLO para el healthcheck del TRD §6.5 (la imagen Temurin no lo trae;
# el backend no publica puertos, no hay alternativa del lado host — RG-4)
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/*.jar /app/app.jar
# Documental: no publica nada (sin ports: en la compose, R10)
EXPOSE 8080
# Sin HEALTHCHECK en el Dockerfile: los healthchecks viven en compose.yaml (DD-5)
ENTRYPOINT ["java", "-jar", "/app/app.jar"]