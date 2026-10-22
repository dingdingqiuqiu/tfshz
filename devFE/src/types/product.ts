export interface Product {
  id: number
  shopId: number
  name: string
  description?: string
  image?: string
  /** 单价，单位：分 */
  price: number
  stock?: number
  status?: number
  sort?: number
}

/** 商品订单（后端 ProductOrderVO） */
export interface ProductOrder {
  /** 19 位订单号，后端按字符串返回 */
  orderId: string
  shopId: number
  shopName?: string
  /** 单位：分 */
  totalAmount: number
  /** 1 待支付 / 2 待核销 / 3 已核销 / 4 已关闭 */
  status: number
  statusText?: string
  verifyCode?: string
  expireTime?: string
  payTime?: string
  verifyTime?: string
  createTime?: string
  items: ProductOrderItem[]
}

export interface ProductOrderItem {
  productName: string
  price: number
  quantity: number
  amount: number
}
