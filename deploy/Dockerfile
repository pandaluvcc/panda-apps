# 简单直接方案 - 你已经把 panda-api-1.0.0-SNAPSHOT.jar 和 lib/ 传到服务器了
# 这个文件保存为 /data/docker/platform/panda-apps/Dockerfile 覆盖原来的

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 复制你已经上传好的文件（这些已经在服务器上了）
COPY panda-api-1.0.0-SNAPSHOT.jar /app/
COPY lib/ /app/lib/
COPY config/ /app/config/

# 创建日志目录
RUN mkdir -p /app/logs

# 暴露端口
EXPOSE 9090

# JVM 参数
ENV JAVA_OPTS="-Xms300m -Xmx500m -XX:+UseG1GC -XX:MaxGCPauseMillis=100"

# 启动命令
CMD java $JAVA_OPTS -cp "panda-api-1.0.0-SNAPSHOT.jar:lib/*" \
  -Dspring.config.location=./config/application-prod.yml \
  com.panda.PandaApplication
