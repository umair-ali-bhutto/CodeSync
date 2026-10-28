FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /app

COPY pom.xml .

RUN mvn dependency:go-offline -B

COPY src ./src

RUN mvn clean package -DskipTests


FROM tomcat:11-jre25-temurin-noble

RUN rm -rf /usr/local/tomcat/webapps/*

COPY --from=build /app/target/codesync.war /usr/local/tomcat/webapps/codesync.war

EXPOSE 8080

CMD ["catalina.sh", "run"]