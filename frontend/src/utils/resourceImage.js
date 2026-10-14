// 资源配图工具：列表卡片与详情页共用同一套规则，保证"外部与内部图片一致"
const typeImages = {
  1: '/assets/type-classroom.png',
  2: '/assets/type-lab.png',
  3: '/assets/type-library.png',
  4: '/assets/type-gym.png'
}

// 体育馆各场馆实拍图（按资源名称匹配）
const gymImageMap = {
  '游泳馆': '/assets/gym-swimming.png',
  '乒乓球馆': '/assets/gym-tabletennis.png',
  '篮球场': '/assets/gym-basketball.png',
  '羽毛球场': '/assets/gym-badminton.png',
  '网球场': '/assets/gym-tennis.png',
  '排球场': '/assets/gym-volleyball.png'
}

/**
 * 根据资源对象返回配图地址
 * - 体育馆：按场馆名取实拍图（游泳馆 → gym-swimming.png）
 * - 其他资源：按资源类型取通用图
 */
export function resourceImage(resource) {
  if (!resource) return typeImages[1]
  if (resource.resourceType === 4 && gymImageMap[resource.name]) {
    return gymImageMap[resource.name]
  }
  return typeImages[resource.resourceType] || typeImages[1]
}

export default resourceImage
