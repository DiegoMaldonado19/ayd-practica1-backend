FROM maven:3.9-eclipse-temurin-25
WORKDIR /app

# Copy the POM first so the dependency layer is cached between source changes
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

EXPOSE 8080

CMD ["mvn", "spring-boot:run"]
