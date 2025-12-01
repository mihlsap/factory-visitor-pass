FROM amazoncorretto:latest as build

RUN apk update && apk add maven

WORKDIR /app

COPY pom.xml .

RUN mvn dependency:go-offline -B

COPY src /app/src

RUN mvn clean package -DskipTests


FROM amazoncorretto:latest

WORKDIR /app

COPY --from=build /app/target/*.jar backend.jar

CMD ["java", "-jar", "backend.jar"]