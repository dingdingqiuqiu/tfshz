import { request } from './http'
import type { Blog, BlogComment, MyVoucherOrder, Shop, ShopType, User, Voucher } from '../types/domain'

export const api = {
  async shopTypes() { return request<ShopType[]>({ method: 'GET', url: '/shop-type/list' }) },
  async hotBlogs(page = 1) { return request<Blog[]>({ method: 'GET', url: '/blog/hot', params: { current: page } }) },
  async shopsByType(typeId: number, page = 1) { return request<Shop[]>({ method: 'GET', url: '/shop/of/type', params: { typeId, current: page } }) },
  async shopsByName(name: string, page = 1) { return request<Shop[]>({ method: 'GET', url: '/shop/of/name', params: { name, current: page } }) },
  async shop(id: number) { return request<Shop>({ method: 'GET', url: `/shop/${id}` }) },
  async shopList(page = 1, size = 50) { return request<Shop[]>({ method: 'GET', url: '/shop/list', params: { current: page, size } }) },
  async vouchers(shopId: number) { return request<Voucher[]>({ method: 'GET', url: `/voucher/list/${shopId}` }) },
  /** 秒杀抢券：返回订单号，订单落库是异步的 */
  async seckill(voucherId: number) { return request<number>({ method: 'POST', url: `/voucher-order/seckill/${voucherId}` }) },
  /** 我的优惠券 */
  async myCoupons() { return request<MyVoucherOrder[]>({ method: 'GET', url: '/voucher-order/list' }) },
  /** 收藏 / 取消收藏店铺 */
  async favoriteShop(shopId: number, isFavorite: boolean) { return request<unknown>({ method: 'PUT', url: `/shop-favorite/${shopId}/${isFavorite}` }) },
  /** 是否已收藏该店铺 */
  async isShopFavorite(shopId: number) { return request<boolean>({ method: 'GET', url: `/shop-favorite/or/not/${shopId}` }) },
  /** 我收藏的店铺 */
  async myFavorites() { return request<Shop[]>({ method: 'GET', url: '/shop-favorite/list' }) },
  async blog(id: number) { return request<Blog>({ method: 'GET', url: `/blog/${id}` }) },
  async likeBlog(id: number) { return request<unknown>({ method: 'PUT', url: `/blog/like/${id}` }) },
  async blogComments(blogId: number, page = 1) { return request<BlogComment[]>({ method: 'GET', url: '/blog-comments/of/blog', params: { blogId, current: page } }) },
  async commentReplies(parentId: number) { return request<BlogComment[]>({ method: 'GET', url: `/blog-comments/reply/${parentId}` }) },
  async addComment(data: { blogId: number; content: string }) { return request<unknown>({ method: 'POST', url: '/blog-comments', data }) },
  async likeComment(id: number) { return request<{ liked?: number; isLiked?: boolean }>({ method: 'PUT', url: `/blog-comments/like/${id}` }) },
  async user(id: number) { return request<User>({ method: 'GET', url: `/user/${id}` }) },
  async me() { return request<User>({ method: 'GET', url: '/user/me' }) },
  async login(payload: { phone: string; code?: string; password?: string }) { return request<User>({ method: 'POST', url: '/user/login', data: payload }) },
  async logout() {
    return request<unknown>({ method: 'POST', url: '/user/logout' })
  },
}