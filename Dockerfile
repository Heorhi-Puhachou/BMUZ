# Вобраз з бэкэндам і старонкамі разам.
# Тэсты тут не запускаюцца: ім патрэбны Docker; іх запускае `./mvnw test`.

FROM node:22-alpine AS frontend
WORKDIR /build
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-fund --no-audit
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:21-jdk AS backend
WORKDIR /build
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY backend/src src
COPY --from=frontend /build/dist src/main/resources/static
RUN ./mvnw -q -B package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 bmuz
WORKDIR /app
COPY --from=backend /build/target/*.jar app.jar
COPY data/catalogue.json data/catalogue.json
USER bmuz
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
