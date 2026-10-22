# 后端镜像：多阶段构建（Maven 打包 → JRE 运行）
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# 先只拷 pom，利用 Docker 层缓存：依赖没变就不重复下载
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Dfile.encoding=UTF-8", "-jar", "app.jar"]
