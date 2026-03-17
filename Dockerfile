# ==================== 构建阶段 ====================
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# 复制 pom 文件（利用 Docker 缓存层）
COPY pom.xml .
COPY common/pom.xml common/
COPY app-gridtrading/pom.xml app-gridtrading/
COPY app-snapledger/pom.xml app-snapledger/
COPY panda-api/pom.xml panda-api/

# 下载依赖（利用缓存）
RUN mvn dependency:go-offline -B

# 复制源代码
COPY common/ common/
COPY app-gridtrading/ app-gridtrading/
COPY app-snapledger/ app-snapledger/
COPY panda-api/ panda-api/

# 构建应用
RUN mvn clean package -DskipTests -B

# ==================== 运行阶段 ====================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 创建非 root 用户
RUN addgroup -S panda && adduser -S panda -G panda

# 从构建阶段复制 jar 文件
COPY --from=builder /build/panda-api/target/*.jar app.jar

# 设置文件所有权
RUN chown -R panda:panda /app

# 切换到非 root 用户
USER panda

# 暴露端口
EXPOSE 9090

# JVM 优化参数
ENV JAVA_OPTS="-Xms400m -Xmx550m -XX:+UseG1GC -XX:MaxGCPauseMillis=100"

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
