import type { RouteRecordRaw } from 'vue-router'
import HomeView from './views/HomeView.vue'
import DiscoverView from './views/DiscoverView.vue'
import ShopListView from './views/ShopListView.vue'
import ShopDetailView from './views/ShopDetailView.vue'
import BlogDetailView from './views/BlogDetailView.vue'
import UserDetailView from './views/UserDetailView.vue'
import ProfileView from './views/ProfileView.vue'
import LoginView from './views/LoginView.vue'
import OrderDetailView from './views/OrderDetailView.vue'
import VerifyView from './views/VerifyView.vue'

export const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/home' },
  { path: '/home', component: HomeView, meta: { title: '首页' } },
  { path: '/discover', component: DiscoverView, meta: { title: '发现' } },
  { path: '/shops', component: ShopListView, meta: { title: '商户' } },
  { path: '/shops/:id', component: ShopDetailView, meta: { title: '商户详情' } },
  { path: '/blogs/:id', component: BlogDetailView, meta: { title: '文章详情' } },
  { path: '/users/:id', component: UserDetailView, meta: { title: '用户资料' } },
  // 订单详情：type = product（商品单）| voucher（秒杀券单），两者共用同一套支付/核销页面
  { path: '/orders/:type(product|voucher)/:id', component: OrderDetailView, meta: { title: '订单详情' } },
  // 商家核销台（演示）
  { path: '/verify', component: VerifyView, meta: { title: '核销台' } },
  { path: '/profile', component: ProfileView, meta: { title: '我的' } },
  { path: '/login', component: LoginView, meta: { title: '登录' } },
  // 兜底：/login.html 这类旧版静态页地址已不存在，不能再让页面停在匹配不到路由的 URL 上
  { path: '/:pathMatch(.*)*', redirect: '/home' },
]
