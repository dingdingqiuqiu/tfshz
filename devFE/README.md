# 听风生活志 · 前端（devFE）

Vue 3 + TypeScript + Vite 的大众点评风格前端，配套后端见仓库根目录 [`README.md`](../README.md)。

## 页面与能力

| 路由 | 页面 | 依赖接口 |
|---|---|---|
| `/home` | 首页：分类、热门笔记、精选店铺 | `/shop-type/list`、`/blog/hot`、`/shop/of/type` |
| `/discover` | 发现：笔记流、点赞 | `/blog/hot`、`/blog/like/{id}` |
| `/shops` | 找店：分类 / 关键词 / 全部 | `/shop/list`、`/shop/of/type`、`/shop/of/name` |
| `/shops/:id` | 商户详情：**商品加购物车 → 结算**、秒杀券抢购、店铺收藏 | `/product/of/shop/{id}`、`/voucher/list/{id}`、`/shop-favorite/*` |
| `/blogs/:id` | 笔记详情：多图轮播、评论、评论点赞 | `/blog/{id}`、`/blog-comments/*` |
| `/orders/:type/:id` | 订单页：**立即支付（Mock）→ 核销二维码 → 取消**，商品单与券单共用 | `/product-order/*` 或 `/voucher-order/*` |
| `/verify` | 商家核销台：输入/扫码核销 | `/order/verify/{code}` |
| `/profile` | 我的：**订单流（商品 + 秒杀券）**、店铺收藏、统计 | `/product-order/list`、`/voucher-order/list`、`/shop-favorite/list` |
| `/login` | 登录（短信验证码） | `/user/code`、`/user/login` |

## 本地开发

```bash
pnpm install
pnpm dev        # http://127.0.0.1:5173
```

Vite 代理（见 `vite.config.ts`）：

| 前缀 | 目标 | 说明 |
|---|---|---|
| `/api` | `http://localhost:8081`（去掉 `/api` 前缀） | 业务接口 |
| `/imgs` | `http://localhost:8081` | 文章图、头像等（后端把 `/imgs/**` 映射到磁盘） |
| `/types` | `http://localhost:8081/imgs/types` | 分类图标（DB 里存的是 `/types/xxx.png`） |

> 图片一律用后端返回的相对路径，不要在组件里写死域名；`src/utils/images.ts` 统一处理「取第一张 + 加载失败兜底」。

## 构建与部署

```bash
pnpm build      # 产物在 dist/
```

部署参考 [`nginx.conf.example`](nginx.conf.example)：`/api/`、`/imgs/`、`/types/` 反向代理 + SPA 路由回退（`try_files $uri $uri/ /index.html`）。

## 实现要点

- **统一响应解包**：后端统一 `Result(success, errorMsg, data, total)`，`services/http.ts` 在拦截器里解包并统一抛错；
- **会话**：双 token 放 `sessionStorage`；401 时静默刷新（single-flight），刷新失败跳 SPA 路由 `/login`（带 `redirect` 回跳），不再硬跳 `/login.html`；
- **二维码**：`src/components/QrCode.vue` 用 vendored 的 `qrcode-generator`（MIT）在浏览器本地生成 SVG，不依赖外部图片接口；
- **订单号**：19 位订单号超过 JS 安全整数，后端一律以字符串返回，前端全链路按 string 处理；
- **我的订单**：商品单与秒杀券单在 UI 层合并成一条流（类型徽标区分），因为两者的生命周期已在后端统一。
