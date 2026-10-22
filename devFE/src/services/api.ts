import { request } from './http'
import type { Blog, BlogComment, MyVoucherOrder, Shop, ShopType, User, Voucher } from '../types/domain'
import type { Product, ProductOrder } from '../types/product'

export const api = {
  async shopTypes() { return request<ShopType[]>({ method: 'GET', url: '/shop-type/list' }) },
  async hotBlogs(page = 1) { return request<Blog[]>({ method: 'GET', url: '/blog/hot', params: { current: page } }) },
  async shopsByType(typeId: number, page = 1) { return request<Shop[]>({ method: 'GET', url: '/shop/of/type', params: { typeId, current: page } }) },
  async shopsByName(name: string, page = 1) { return request<Shop[]>({ method: 'GET', url: '/shop/of/name', params: { name, current: page } }) },
  async shop(id: number) { return request<Shop>({ method: 'GET', url: `/shop/${id}` }) },
  async shopList(page = 1, size = 50) { return request<Shop[]>({ method: 'GET', url: '/shop/list', params: { current: page, size } }) },
  async vouchers(shopId: number) { return request<Voucher[]>({ method: 'GET', url: `/voucher/list/${shopId}` }) },
  /** 秒杀抢券：返回订单号（字符串，避免 19 位数字丢精度），订单落库是异步的 */
  async seckill(voucherId: number) { return request<string>({ method: 'POST', url: `/voucher-order/seckill/${voucherId}` }) },
  /** 我的券订单 */
  async myCoupons() { return request<MyVoucherOrder[]>({ method: 'GET', url: '/voucher-order/list' }) },
  /** 券订单详情（支付页 / 核销码页） */
  async voucherOrderDetail(orderId: string) { return request<MyVoucherOrder>({ method: 'GET', url: `/voucher-order/${orderId}` }) },
  /** 支付券订单（与商品订单共用 Mock 支付通道） */
  async payVoucherOrder(orderId: string) { return request<MyVoucherOrder>({ method: 'POST', url: `/voucher-order/${orderId}/pay` }) },
  /** 取消未支付的券订单 */
  async cancelVoucherOrder(orderId: string) { return request<MyVoucherOrder>({ method: 'POST', url: `/voucher-order/${orderId}/cancel` }) },
  /** 收藏 / 取消收藏店铺 */
  async favoriteShop(shopId: number, isFavorite: boolean) { return request<unknown>({ method: 'PUT', url: `/shop-favorite/${shopId}/${isFavorite}` }) },
  /** 是否已收藏该店铺 */
  async isShopFavorite(shopId: number) { return request<boolean>({ method: 'GET', url: `/shop-favorite/or/not/${shopId}` }) },
  /** 我收藏的店铺 */
  async myFavorites() { return request<Shop[]>({ method: 'GET', url: '/shop-favorite/list' }) },
  /** 店铺在架商品 */
  async products(shopId: number) { return request<Product[]>({ method: 'GET', url: `/product/of/shop/${shopId}` }) },
  /** 下单（待支付） */
  async createOrder(payload: { shopId: number; items: { productId: number; quantity: number }[] }) {
    return request<ProductOrder>({ method: 'POST', url: '/product-order', data: payload })
  },
  /** 我的商品订单 */
  async myOrders() { return request<ProductOrder[]>({ method: 'GET', url: '/product-order/list' }) },
  /** 订单详情 */
  async orderDetail(orderId: string) { return request<ProductOrder>({ method: 'GET', url: `/product-order/${orderId}` }) },
  /** 支付（演示通道，不调第三方） */
  async payOrder(orderId: string) { return request<ProductOrder>({ method: 'POST', url: `/product-order/${orderId}/pay` }) },
  /** 取消未支付订单 */
  async cancelOrder(orderId: string) { return request<ProductOrder>({ method: 'POST', url: `/product-order/${orderId}/cancel` }) },
  /** 统一核销：商品订单和秒杀券订单共用 */
  async verifyOrder(code: string) { return request<unknown>({ method: 'POST', url: `/order/verify/${encodeURIComponent(code)}` }) },
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