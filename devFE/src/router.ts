import type { RouteRecordRaw } from 'vue-router'
import HomeView from './views/HomeView.vue'
import DiscoverView from './views/DiscoverView.vue'
import ShopListView from './views/ShopListView.vue'
import ShopDetailView from './views/ShopDetailView.vue'
import ProfileView from './views/ProfileView.vue'
import LoginView from './views/LoginView.vue'

export const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/home' },
  { path: '/home', component: HomeView, meta: { title: '首页' } },
  { path: '/discover', component: DiscoverView, meta: { title: '发现' } },
  { path: '/shops', component: ShopListView, meta: { title: '商户' } },
  { path: '/shops/:id', component: ShopDetailView, meta: { title: '商户详情' } },
  { path: '/profile', component: ProfileView, meta: { title: '我的' } },
  { path: '/login', component: LoginView, meta: { title: '登录' } },
]
