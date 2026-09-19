# 听风生活志前端

Vue 3 + TypeScript + Vite 的大众点评风格前端骨架，接口基于 `BackEnd/tfshz` 当前控制器实现。

## 本地开发

```bash
pn install
pn dev
```

开发服务器会把 `/api` 代理到 `http://localhost:8081`。后端未启动时，首页、发现页和找店页会展示本地演示数据，方便先做视觉与交互开发。

## Nginx 部署

```bash
pn build
```

将 `dist` 目录部署到 Nginx，并参考 `nginx.conf.example` 配置 `/api/` 反向代理和 SPA 路由回退。

## 页面与接口边界

- 首页：`/shop-type/list`、`/blog/hot`、`/shop/of/type`
- 找店：`/shop/of/type`、`/shop/of/name`
- 商户详情：`/shop/{id}`、`/voucher/list/{shopId}`
- 发现：`/blog/hot`、`/blog/like/{id}`
- 登录与个人中心：`/user/login`、`/user/me`

所有后端响应按 `Result(success, errorMsg, data, total)` 解包，token 从 `sessionStorage.token` 读取并放入 `authorization` 请求头。
