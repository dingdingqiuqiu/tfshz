import type { Blog, Shop, ShopType, User, Voucher } from '../types/domain'

export const demoTypes: ShopType[] = [
  { id: 1, name: '美食', icon: '/assets/type-food.png' },
  { id: 2, name: '休闲娱乐', icon: '/assets/type-ktv.png' },
  { id: 3, name: '丽人', icon: '/assets/type-spa.png' },
  { id: 4, name: '酒店旅行', icon: '/assets/type-hotel.png' },
  { id: 5, name: '咖啡茶饮', icon: '/assets/type-food.png' },
  { id: 6, name: '亲子乐园', icon: '/assets/type-ktv.png' },
]

export const demoBlogs: Blog[] = [
  { id: 101, userId: 1, title: '周末去这里，拍照和下午茶都很出片', content: '藏在街角的生活美学空间，适合和朋友慢慢坐一下午。', images: '/assets/cafe.jpg', liked: 328, comments: 42, name: '小满的城市漫游', icon: '/assets/avatar.png' },
  { id: 102, userId: 2, title: '本地人私藏的夜宵地图，收藏这一篇就够了', content: '烟火气十足的小店，价格友好，分量也很足。', images: '/assets/food.jpg', liked: 186, comments: 26, name: '阿黎吃不饱', icon: '/assets/avatar.png' },
  { id: 103, userId: 3, title: '一场不赶时间的城市夜游', content: '沿江散步，看灯光亮起来，城市的另一面很温柔。', images: '/assets/night.jpg', liked: 512, comments: 61, name: '晚风计划', icon: '/assets/avatar.png' },
]

export const demoShops: Shop[] = [
  { id: 1, name: '南岸咖啡研究所', typeId: 1, area: '滨江区', address: '江南大道 188 号', avgPrice: 42, sold: 2380, comments: 329, score: 48, openHours: '08:00-22:30', images: '/assets/cafe.jpg', distance: 0.8 },
  { id: 2, name: '阿婆家常菜', typeId: 1, area: '上城区', address: '河坊街 76 号', avgPrice: 68, sold: 5630, comments: 812, score: 47, openHours: '10:30-21:30', images: '/assets/food.jpg', distance: 1.2 },
  { id: 3, name: '拾光江景餐厅', typeId: 1, area: '钱塘区', address: '之江路 99 号', avgPrice: 126, sold: 980, comments: 156, score: 49, openHours: '11:00-23:00', images: '/assets/night.jpg', distance: 2.6 },
]

export const demoVouchers: Voucher[] = [
  { id: 11, title: '双人下午茶套餐', subTitle: '招牌咖啡 2 杯 + 手作甜点', payValue: 880, actualValue: 1180, stock: 26, type: 1 },
  { id: 12, title: '满 100 减 20 代金券', subTitle: '全场通用，不限时段', payValue: 800, actualValue: 1000, stock: 99, type: 0 },
]

export const demoUser: User = { id: 1, nickName: '城市探索员', icon: '/assets/avatar.png' }
