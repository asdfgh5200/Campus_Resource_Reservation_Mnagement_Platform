/**
 * 记忆用户上次浏览的资源分类，避免从资源列表返回首页时又被重置回「教室」。
 * 分类类型：1 教室 / 2 实验室 / 3 图书馆 / 4 体育馆（与路由 /category/:type 一致）。
 * 用 sessionStorage 而非 localStorage：关闭标签页后重新来过，不影响新会话的默认落点。
 */
const KEY = 'lastCategoryType'

/** 保存当前分类类型，非法值直接忽略 */
export function saveLastCategory(type) {
  const n = Number(type)
  if (n >= 1 && n <= 4) {
    sessionStorage.setItem(KEY, String(n))
  }
}

/** 读取上次分类类型，无记录或值非法时返回 fallback（默认教室） */
export function readLastCategory(fallback = 1) {
  const n = Number(sessionStorage.getItem(KEY))
  return n >= 1 && n <= 4 ? n : fallback
}
