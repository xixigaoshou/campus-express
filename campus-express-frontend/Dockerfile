# ============================================================
# 校园快递代取平台 - 前端镜像（多阶段构建：Node 构建 → Nginx 托管静态资源）
# 单独构建：docker build -t campus-express-frontend .
# 推荐用法：在项目根目录执行 docker compose up --build
# ============================================================

# ---------- 阶段一：构建静态资源 ----------
FROM node:20-alpine AS build
WORKDIR /build
# 先装依赖：仅改业务代码时命中缓存，不必重复 npm install
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# ---------- 阶段二：Nginx 托管 ----------
FROM nginx:1.27-alpine
# 用自定义配置替换默认站点：托管 dist 静态资源 + 反向代理后端接口/上传资源/WebSocket
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]