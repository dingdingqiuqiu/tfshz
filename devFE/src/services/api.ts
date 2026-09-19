import { request } from './http'
import type { Blog, Shop, ShopType, User, Voucher } from '../types/domain'

export const api = {
  async shopTypes() { return request<ShopType[]>({ method: 'GET', url: '/shop-type/list' }) },
  async hotBlogs(page = 1) { return request<Blog[]>({ method: 'GET', url: '/blog/hot', params: { current: page } }) },
  async shopsByType(typeId: number, page = 1) { return request<Shop[]>({ method: 'GET', url: '/shop/of/type', params: { typeId, current: page } }) },
  async shopsByName(name: string, page = 1) { return request<Shop[]>({ method: 'GET', url: '/shop/of/name', params: { name, current: page } }) },
  async shop(id: number) { return request<Shop>({ method: 'GET', url: `/shop/${id}` }) },
  async vouchers(shopId: number) { return request<Voucher[]>({ method: 'GET', url: `/voucher/list/${shopId}` }) },
  async blog(id: number) { return request<Blog>({ method: 'GET', url: `/blog/${id}` }) },
  async likeBlog(id: number) { return request<unknown>({ method: 'PUT', url: `/blog/like/${id}` }) },
  async me() { return request<User>({ method: 'GET', url: '/user/me' }) },
  async login(payload: { phone: string; code?: string; password?: string }) { return request<User>({ method: 'POST', url: '/user/login', data: payload }) },
}