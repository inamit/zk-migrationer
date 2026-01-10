FROM openjdk:17-slim

WORKDIR /app

COPY target/zookeeper-migration-tool-1.0-SNAPSHOT.jar /app/zookeeper-migration-tool.jar

ENTRYPOINT ["java", "-jar", "/app/zookeeper-migration-tool.jar"]
