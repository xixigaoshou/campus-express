# ============================================================
# 校园快递代取平台 - 后端镜像（多阶段构建：Maven 编译 → JRE 运行）
# 单独构建：docker build -t campus-express-backend .
# 单独运行：docker run -p 8080:8080 -e JWT_SECRET=<密钥> campus-express-backend
# 推荐用法：在项目根目录执行 docker compose up --build（会自动注入数据库连接）
# ============================================================

# ---------- 阶段一：编译打包 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# 先只拷贝 pom.xml 并预下载依赖，利用镜像层缓存：仅改源码时不会重复拉取依赖
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:resolve
# 再拷贝源码并打包（跳过测试：镜像构建只负责产出可执行 JAR，测试由 CI 负责）
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---------- 阶段二：运行 ----------
FROM eclipse-temurin:17-jre
WORKDIR /app
# 上传目录与日志目录必须在容器内可写（compose 中挂载为数据卷持久化）
RUN mkdir -p /app/uploads/avatar /app/uploads/order /app/logs
COPY --from=build /build/target/*.jar /app/app.jar
EXPOSE 8080
# 默认以生产 profile 启动：关闭 SQL 标准输出、验证码不回传（需注入 JWT_SECRET，否则启动即失败）
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "/app/app.jar"]