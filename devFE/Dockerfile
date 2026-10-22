# 前端镜像：Node 构建 → Nginx 托管
FROM node:20-alpine AS build
WORKDIR /build
RUN npm config set registry https://registry.npmmirror.com && npm i -g pnpm@9
# 注意：不要 COPY pnpm-workspace.yaml。它只为本地 pnpm 的 allowBuilds 配置而生，
# 容器里若存在却没有 packages 字段，pnpm 会直接报 "packages field missing or empty"。
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

FROM nginx:1.27-alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.docker.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
