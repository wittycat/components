# JDK17 官方轻量运行时镜像
FROM eclipse-temurin:17-jre

# 容器工作目录
WORKDIR /app

# 复制打包好的jar包到镜像里面名字叫app.jar
COPY target/springboot-1.0.jar app.jar

# SpringBoot默认端口
EXPOSE 8080

# 启动镜像里面jar
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

#docker ps
#CONTAINER ID   IMAGE            COMMAND               CREATED          STATUS          PORTS                                         NAMES
#053746b20db4   spring-demo:v1   "java -jar app.jar"   19 minutes ago   Up 19 minutes   0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp   springboot-app

#1个node（虚拟机或者物理机）->1个或多个pod->一般一个pod里面一个容器：镜像的实例
#https://www.bilibili.com/video/BV1Se411r7vY?spm_id_from=333.788.videopod.sections&vd_source=cf47bbb62fe30d65e7f4d00506567f69