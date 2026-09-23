# ============================================================
# 后端镜像：多阶段构建
#   构建阶段用 JDK 编译，运行阶段只保留 JRE
#   好处：镜像体积更小、攻击面更窄
# ============================================================

# ---------- 构建阶段 ----------
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /build

# 先只复制构建描述文件并预热依赖，让这一层能被 Docker 缓存复用：
# 只要 pom.xml 不变，改业务代码就不会重新下载依赖。
COPY mvnw .
COPY .mvn/ .mvn/
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -DskipTests package

# ---------- 运行阶段 ----------
FROM eclipse-temurin:25-jre AS runtime

# curl 仅用于容器健康检查
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# 以非 root 用户运行；uid/gid 固定，便于后续挂载卷时对齐权限
RUN groupadd --system --gid 1001 diary \
    && useradd --system --uid 1001 --gid diary --home-dir /app --shell /usr/sbin/nologin diary

WORKDIR /app

COPY --from=builder --chown=diary:diary /build/target/diary-backend-*.jar /app/app.jar

USER diary

EXPOSE 8080

# 不写死 -Xmx：交给容器内存上限自适应，避免「宿主机内存大但容器限额小」时堆溢出
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
