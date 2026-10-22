# 图片流转路径文档

## 第一性分解

### 基本事实
1. **图片必须存在于某处** - 物理存储
2. **图片必须通过网络传输** - HTTP 请求
3. **浏览器必须知道从哪里获取** - URL 寻址

---

## 三类图片的流转

### 1️⃣ 店铺图片（Shop Images）

**存储位置**
- 位置：美团、大众点评等第三方服务器
- URL 格式：`https://img.meituan.net/...` 或 `https://qcloud.dpfile.com/...`

**数据流**
```
前端页面
  ↓ 请求
浏览器直接访问第三方 URL
  ↓ 响应
HTTPS 下载完整图片
  ↓
浏览器渲染
```

**为何成功**
- 完整外部 URL，浏览器直接可达
- 无跨域问题（HTTPS）
- 无认证需求

---

### 2️⃣ 分类图片（ShopType Images）

**存储位置**
- 物理位置：`D:\1devCode\tfshz\FrontEnd\nginx-1.18.0\html\tfsh\imgs\types\`
- 文件名：`ms.png`、`KTV.png` 等

**数据来源**
- 后端数据库：`ShopType.icon` = `/types/ms.png`（相对路径）
- 静态文件，不变更

**流转路径**
```
1. 数据库查询
   ShopTypeServiceImpl.list()
   → SELECT * FROM tb_shop_type
   → icon 字段返回 "/types/ms.png"

2. 后端 API 返回
   GET /api/shop-type/list
   → JSON: {"icon": "/types/ms.png", ...}

3. 前端接收
   js: api.shopTypes()
   → 存储到 types.value[]

4. 模板中渲染
   <img :src="typeIcon(icon)" />
   
5. typeIcon() 处理
   输入："/types/ms.png"
   → 正则匹配 /types/ 开头
   → 直接返回 "/types/ms.png"

6. 浏览器发起请求
   GET http://localhost:5173/types/ms.png

7. Vite 代理拦截
   /types/** → http://localhost:8080/tfsh/types/ms.png

8. Nginx 提供文件
   location /tfsh/ { root html; }
   → 查找 html/tfsh/types/ms.png
   → 200 返回文件

9. 浏览器渲染
```

**关键角色**
| 组件 | 职责 |
|------|------|
| 数据库 | 存储图片相对路径 |
| 后端 API | 返回相对路径 |
| 前端 typeIcon() | 验证路径合法性 |
| Vite 代理 | 转发 /types/ 到 Nginx |
| Nginx | 静态文件服务器 |
| 浏览器 | HTTP 请求和渲染 |

---

### 3️⃣ 博客图片（Blog Images）

**存储位置**
- 物理位置：`D:\1devCode\tfshz\FrontEnd\nginx-1.18.0\html\tfsh\imgs\blogs\{d1}\{d2}\`
- 文件名：`{uuid}.{suffix}`
- 例：`/imgs/blogs/7/14/4771fefb-1a87-4252-816c-9f7ec41ffa4a.jpg`

**如何生成存储路径**

后端上传时：
```
1. 客户端上传文件
   POST /upload/blog
   → MultipartFile image

2. UploadController 处理
   String uuid = UUID.randomUUID()
   int hash = uuid.hashCode()
   int d1 = hash & 0xF          // 取低 4 位
   int d2 = (hash >> 4) & 0xF   // 取次 4 位
   
   生成目录：/blogs/7/14/
   生成文件：{uuid}.jpg
   
   最终路径：/blogs/7/14/{uuid}.jpg

3. 文件保存
   file.transferTo(
     new File(IMAGE_UPLOAD_DIR, "/blogs/7/14/{uuid}.jpg")
   )
   → IMAGE_UPLOAD_DIR = "D:\1devCode\tfshz\FrontEnd\nginx-1.18.0\html\tfsh\imgs\"
   → 实际保存：D:\1devCode\tfshz\FrontEnd\nginx-1.18.0\html\tfsh\imgs\blogs\7\14\{uuid}.jpg

4. 返回相对路径给前端
   return Result.ok("/blogs/7/14/{uuid}.jpg")
```

**数据流**
```
1. 后端 API 返回
   GET /api/blog/hot
   → JSON: {"images": "/imgs/blogs/7/14/{uuid}.jpg,..."}

2. 前端存储
   js: blogs.value[]
   blog.images = "/imgs/blogs/7/14/{uuid}.jpg"

3. BlogCard 组件
   <img :src="blog.images?.split(',')[0]" />
   → 取第一张图片

4. 浏览器发起请求
   GET http://localhost:5173/imgs/blogs/7/14/{uuid}.jpg

5. Vite 代理拦截
   /imgs/** → http://localhost:8080/tfsh/imgs/blogs/7/14/{uuid}.jpg

6. Nginx 提供文件
   location /tfsh/ { root html; }
   → 查找 html/tfsh/imgs/blogs/7/14/{uuid}.jpg
   → 200 返回文件

7. 浏览器渲染
```

**路径分解**

| 组件 | 内容 |
|------|------|
| 相对路径 | `/imgs/blogs/7/14/{uuid}.jpg` |
| 实际磁盘 | `D:\...\nginx-1.18.0\html\tfsh\imgs\blogs\7\14\{uuid}.jpg` |
| Vite 转发 | `http://localhost:8080/tfsh/imgs/blogs/7/14/{uuid}.jpg` |
| Nginx 根路径 | `html/` |
| 最终查找 | `html/tfsh/imgs/blogs/7/14/{uuid}.jpg` ✓ 匹配 |

---

## 为什么之前失败

### 根本原因
前端请求 → `http://localhost:8081/imgs/...`（Spring Boot）
- Spring Boot 没有这些文件
- 认证拦截器拦截请求 → 401

### 解决链条
1. ❌ 后端没有 `/imgs/` 处理
   - 文件其实在 Nginx（8080）
   
2. ❌ 前端不知道去 Nginx 取
   - Vite 代理默认指向 Spring Boot（8081）
   
3. ✅ 添加 Vite 代理规则
   ```js
   '/imgs': { target: 'http://localhost:8080/tfsh' }
   ```
   - 前端 `/imgs/...` → Nginx `/tfsh/imgs/...`
   
4. ✅ 修改 Nginx 配置
   ```nginx
   location /tfsh/ { root html; }
   ```
   - Nginx 知道如何提供 `/tfsh/` 下的文件

---

## 架构设计原理

**分离关注**
| 服务 | 职责 |
|------|------|
| Spring Boot (8081) | 业务逻辑、数据库、API |
| Nginx (8080) | 静态文件服务 |
| Vite (5173) | 前端开发、代理转发 |
| 浏览器 | HTTP 客户端 |

**为什么分离**
- 降低 Java 应用负载（不处理静态文件 I/O）
- 利用 Nginx 高效的文件服务能力
- 前后端解耦

---

## 快速排查清单

| 问题 | 诊断 | 解决 |
|------|------|------|
| 图片 404 | `curl http://localhost:8080/tfsh/imgs/types/ms.png` | 检查 Nginx 配置和文件存在 |
| 401 错误 | 请求去了 Spring Boot | 检查 Vite 代理规则 |
| 后端返回 null | 数据库数据缺失 | 检查 upload 端点是否执行 |
| 文件保存失败 | 检查 `IMAGE_UPLOAD_DIR` | 路径权限、磁盘空间 |
