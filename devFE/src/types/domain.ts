export interface Result<T> {
  success: boolean
  errorMsg?: string
  data: T
  total?: number
}

export type LoginData = { accessToken: string; refreshToken: string; user?: User }

export interface ShopType { id: number; name: string; icon?: string }

export interface Shop {
  id: number
  name: string
  typeId: number
  images?: string
  area?: string
  address?: string
  avgPrice?: number
  sold?: number
  comments?: number
  score?: number
  openHours?: string
  distance?: number
}

export interface Blog {
  id: number
  userId: number
  shopId?: number
  title: string
  content?: string
  images?: string
  liked?: number
  comments?: number
  name?: string
  icon?: string
  isLike?: boolean
  createTime?: string
}

export interface BlogComment {
  id: number
  userId: number
  blogId: number
  parentId: number
  answerId: number
  content: string
  liked: number
  status: boolean
  createTime: string
  /** 评论展示用：后端在查询时补上的用户昵称与头像 */
  userName?: string
  icon?: string
  /** 当前登录用户是否点过赞（后端按 Redis 去重记录返回） */
  isLiked?: boolean
}

export interface User { id: number; nickName: string; icon?: string }
export interface Voucher { id: number; title: string; subTitle?: string; payValue: number; actualValue: number; type?: number; stock?: number; beginTime?: string; endTime?: string }

/** 我的优惠券列表项：订单 + 券 + 店铺（后端 VoucherOrderVO） */
export interface MyVoucherOrder {
  /** 订单号是 19 位数字，后端按字符串返回，避免 JS 精度丢失 */
  orderId: string
  voucherId: number
  title?: string
  subTitle?: string
  shopId?: number
  shopName?: string
  payValue?: number
  actualValue?: number
  /** 1 未支付 / 2 已支付 / 3 已核销 / 4 已取消 */
  status?: number
  createTime?: string
}
