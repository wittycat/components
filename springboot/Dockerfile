# JDK17 官方轻量运行时镜像，支持ARM/M系列Mac
FROM eclipse-temurin:17-jre

# 容器工作目录
WORKDIR /app

# 复制打包好的jar包（和pom里artifactId-version保持一致）
COPY target/springboot-1.0.jar app.jar

# SpringBoot默认端口
EXPOSE 8080

# 启动命令
ENTRYPOINT ["java","-jar","app.jar"]


# . 代表当前目录（构建上下文目录 build context）镜像名字以及版本：spring-demo:v1
#docker build -t spring-demo:v1 .

# -d 后台运行
# 运行镜像spring-demo:v1，启动起来的容器实例名字叫springboot-app
#docker run -d -p 8080:8080 --name springboot-app spring-demo:v1

#查看运行容器日志
#docker logs springboot-app

# 停止运行中的容器
#docker stop springboot-app

# 重启容器
#docker restart springboot-app

# 删除已停止的容器（下次run才能重名）
#docker rm springboot-app

