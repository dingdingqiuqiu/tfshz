export interface Result<T> {
  success: boolean
  errorMsg?: string
  data: T
  total?: number
}

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
}

export interface User { id: number; nickName: string; icon?: string }
export interface Voucher { id: number; title: string; subTitle?: string; payValue: number; actualValue: number; type?: number; stock?: number; beginTime?: string; endTime?: string }
