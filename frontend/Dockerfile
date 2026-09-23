# ============================================================
# 前端镜像：多阶段构建
#   构建阶段用 Node 产出静态资源，运行阶段用 Nginx 托管并反代后端
#   这样「前端服务」与「网关」合一，对外只需暴露一个端口
# ============================================================

# ---------- 构建阶段 ----------
FROM node:22-alpine AS builder

WORKDIR /build

# 先复制依赖清单：锁文件未变时，npm ci 这一层可直接命中缓存
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---------- 运行阶段 ----------
FROM nginx:1.31.6-alpine AS runtime

COPY --from=builder /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=5 \
    CMD wget -qO- http://127.0.0.1/ >/dev/null || exit 1
