FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

COPY pom.xml .
COPY cliente/pom.xml cliente/pom.xml
COPY servidor/pom.xml servidor/pom.xml
COPY cordenador-gerenciamento-service/pom.xml cordenador-gerenciamento-service/pom.xml
COPY entidades/pom.xml entidades/pom.xml
COPY entidades/src entidades/src
COPY inscricao-processo-seletivo-service/pom.xml inscricao-processo-seletivo-service/pom.xml
COPY inscricao-processo-seletivo-service/src inscricao-processo-seletivo-service/src

RUN mvn -B -pl entidades,inscricao-processo-seletivo-service -am -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /build/inscricao-processo-seletivo-service/target/dependency/ lib/
COPY --from=builder /build/inscricao-processo-seletivo-service/target/inscricao-processo-seletivo-service-1.0-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-cp", "app.jar:lib/*", "inscricao.Main"]
