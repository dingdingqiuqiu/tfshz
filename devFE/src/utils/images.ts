/**
 * 图片地址的统一处理：后端只返回相对路径（/imgs/...），可能是一串逗号分隔的多图。
 * 这里只做“取第一张 + 加载失败兜底”，不做 URL 拼接 —— 路径由 Vite/Nginx 代理到后端。
 */

export const FALLBACK_BLOG_IMAGE = '/assets/blog1.jpg'
export const FALLBACK_SHOP_IMAGE = '/assets/cafe.jpg'
export const FALLBACK_AVATAR = '/assets/avatar.png'

/** 取逗号分隔多图里的第一张，空值返回兜底图 */
export function firstImage(images?: string | null, fallback = FALLBACK_BLOG_IMAGE) {
  const first = (images || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)[0]
  return first || fallback
}

/** 拆分多图字段，过滤空项 */
export function splitImages(images?: string | null) {
  return (images || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

/**
 * <img @error> 兜底：只在第一次失败时替换成占位图，避免占位图也 404 时无限循环。
 * 用法：<img :src="..." @error="onImageError" /> 或 @error="(e) => onImageError(e, '/assets/cafe.jpg')"
 */
export function onImageError(event: Event, fallback = FALLBACK_BLOG_IMAGE) {
  const img = event.target as HTMLImageElement | null
  if (!img || img.dataset.fallbackApplied === '1') return
  img.dataset.fallbackApplied = '1'
  img.src = fallback
}
