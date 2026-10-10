/**
 * Mock 数据层 —— 纯前端，不连后端
 * 所有 API 函数返回 Promise，同步 resolve mock 数据
 */

// ======== 用户数据 ========
const users = {
  admin: {
    id: 1, username: 'admin', password: 'admin', nickname: '系统管理员',
    role: 1, phone: '13800000000', email: 'admin@campus.edu',
    creditScore: 100, status: 1
  },
  user: {
    id: 2, username: 'user', password: 'user', nickname: '张同学',
    role: 0, phone: '13800001111', email: 'student@campus.edu',
    creditScore: 95, status: 1
  }
}

// ======== 资源数据（程序化生成）========

// 教学楼：2栋 × 5层 × 10间 = 100 间教室
// 实验楼：1栋 × 6层（物理/化学/生物/材料/计算机/机电）× 10间 = 60 间实验室
// 图书馆：3 个区域
// 体育馆：A栋（游泳馆+乒乓球馆）+ B栋（篮球/羽毛球/网球/排球）= 6 个场馆

function genResources() {
  const list = []
  let id = 1

  // --- 教学楼教室 (type=1) ---
  const teachBuildings = ['教学楼A栋', '教学楼B栋']
  for (const bld of teachBuildings) {
    const prefix = bld.includes('A') ? 'A' : 'B'
    for (let floor = 1; floor <= 5; floor++) {
      for (let room = 1; room <= 10; room++) {
        const roomNo = `${prefix}${floor}${String(room).padStart(2, '0')}`
        list.push({
          id: id++, name: `${roomNo} 教室`, resourceType: 1,
          building: bld, floor: `${floor}F`, roomNo,
          capacity: 40 + (room % 3) * 10,
          openTime: '08:00:00', closeTime: '22:00:00',
          maxDuration: 120, status: 1,
          description: `${bld} ${floor}层 ${roomNo}，配备多媒体投影设备，适合课程教学与小组讨论。`,
          longitude: 116.397, latitude: 39.908
        })
      }
    }
  }

  // --- 实验室 (type=2) ---
  const labSubjects = [
    { name: '物理', prefix: 'P', desc: '物理实验专用，配备力学、电学、光学实验仪器。' },
    { name: '化学', prefix: 'C', desc: '化学实验专用，配备通风橱、实验台与安全设施。' },
    { name: '生物', prefix: 'B', desc: '生物实验专用，配备显微镜、培养箱与生物安全柜。' },
    { name: '材料', prefix: 'M', desc: '材料科学实验室，配备拉伸机、硬度计与金相显微镜。' },
    { name: '计算机', prefix: 'CS', desc: '计算机实验室，配备高性能工作站与开发环境。' },
    { name: '机电', prefix: 'ME', desc: '机电实验室，配备数控机床、PLC与机电控制设备。' }
  ]
  for (let s = 0; s < labSubjects.length; s++) {
    const subj = labSubjects[s]
    const floor = s + 1
    for (let room = 1; room <= 10; room++) {
      const roomNo = `${subj.prefix}${floor}${String(room).padStart(2, '0')}`
      list.push({
        id: id++, name: `${roomNo} ${subj.name}实验室`, resourceType: 2,
        building: '实验楼', floor: `${floor}F`, roomNo,
        capacity: 30 + (room % 3) * 5,
        openTime: '08:00:00', closeTime: '20:00:00',
        maxDuration: 180, status: room === 3 && floor === 2 ? 3 : 1,
        description: `实验楼 ${floor}层 ${subj.name}实验室 ${roomNo}，${subj.desc}`,
        longitude: 116.398, latitude: 39.909
      })
    }
  }

  // --- 图书馆 (type=3) ---
  const libAreas = [
    { name: '图书馆三楼自习区', floor: '3F', roomNo: 'F3-A', capacity: 120, desc: '安静宽敞的自习区域，配备独立书桌与电源插座。' },
    { name: '图书馆四楼研讨室', floor: '4F', roomNo: 'F4-B', capacity: 15, desc: '小组研讨专用，配备白板与显示屏。' },
    { name: '图书馆二楼阅览区', floor: '2F', roomNo: 'F2-C', capacity: 80, desc: '开放式阅览区，光线充足，适合长时间自习。' }
  ]
  for (const lib of libAreas) {
    list.push({
      id: id++, name: lib.name, resourceType: 3,
      building: '图书馆', floor: lib.floor, roomNo: lib.roomNo,
      capacity: lib.capacity,
      openTime: '07:00:00', closeTime: '23:00:00',
      maxDuration: 240, status: 1,
      description: lib.desc,
      longitude: 116.396, latitude: 39.907
    })
  }

  // --- 体育馆 (type=4) ---
  const gymVenues = [
    { name: '游泳馆', building: '体育馆A栋', floor: '1F', roomNo: 'GYM-A1', capacity: 50, desc: '标准游泳池，8 条泳道，配备更衣室与热水淋浴。' },
    { name: '乒乓球馆', building: '体育馆A栋', floor: '2F', roomNo: 'GYM-A2', capacity: 20, desc: '10 张标准乒乓球台，适合教学与比赛。' },
    { name: '篮球场', building: '体育馆B栋', floor: '1F', roomNo: 'GYM-B1', capacity: 30, desc: '标准篮球场，可进行全场或半场比赛。' },
    { name: '羽毛球场', building: '体育馆B栋', floor: '2F', roomNo: 'GYM-B2', capacity: 16, desc: '4 片标准羽毛球场地，塑胶地面。' },
    { name: '网球场', building: '体育馆B栋', floor: '1F', roomNo: 'GYM-B3', capacity: 8, desc: '2 片标准网球场，硬地塑胶面层。' },
    { name: '排球场', building: '体育馆B栋', floor: '2F', roomNo: 'GYM-B4', capacity: 24, desc: '标准排球场，可进行教学与比赛。' }
  ]
  for (const g of gymVenues) {
    list.push({
      id: id++, name: g.name, resourceType: 4,
      building: g.building, floor: g.floor, roomNo: g.roomNo,
      capacity: g.capacity,
      openTime: '09:00:00', closeTime: '21:00:00',
      maxDuration: 90, status: 1,
      description: g.desc,
      longitude: 116.399, latitude: 39.910
    })
  }

  return list
}

const resources = genResources()

// ======== 预约数据 ========
let reservations = [
  { id: 1, userId: 2, resourceId: 1, startTime: '2026-09-24 10:00:00', endTime: '2026-09-24 12:00:00', checkinType: 1, checkinFlag: 0, remark: '小组讨论', status: 1, createdAt: '2026-09-23 15:30:00' },
  { id: 2, userId: 2, resourceId: 3, startTime: '2026-09-22 14:00:00', endTime: '2026-09-22 17:00:00', checkinType: 1, checkinFlag: 1, checkinTime: '2026-09-22 13:55:00', remark: '自习', status: 3, createdAt: '2026-09-21 20:10:00' },
  { id: 3, userId: 2, resourceId: 4, startTime: '2026-09-20 16:00:00', endTime: '2026-09-20 18:00:00', checkinType: 2, checkinFlag: 1, checkinTime: '2026-09-20 15:58:00', remark: '羽毛球活动', status: 3, createdAt: '2026-09-19 10:00:00' }
]

let nextReservationId = 4

// ======== 工具函数 ========
function resolve(data, code = 200, message = '操作成功') {
  return Promise.resolve({ code, message, data })
}

function reject(message, code = 400) {
  return Promise.resolve({ code, message, data: null })
}

function delay(data, ms = 300) {
  return new Promise(r => setTimeout(() => r(data), ms))
}

// ======== API 模拟 ========
export const mockApi = {
  // 登录
  login(username, password) {
    const u = users[username]
    if (!u || u.password !== password) {
      return reject('用户名或密码错误', 401)
    }
    const { password: _, ...userInfo } = u
    return delay(resolve({ token: 'mock-token-' + username + '-' + Date.now(), userInfo }))
  },

  // 获取用户信息
  getUserInfo() {
    return delay(resolve({ token: localStorage.getItem('token') || '', userInfo: { id: 2, username: 'user', nickname: '张同学', role: 0, creditScore: 95 } }))
  },

  // 资源分页查询
  getResourcePage({ current = 1, size = 10, type, keyword, status, building, floor } = {}) {
    let list = resources.filter(r => {
      if (type && r.resourceType !== type) return false
      if (status && r.status !== status) return false
      if (building && r.building !== building) return false
      if (floor && r.floor !== floor) return false
      if (keyword) {
        const kw = keyword.toLowerCase()
        if (!r.name.toLowerCase().includes(kw) && !r.building.toLowerCase().includes(kw) && !r.roomNo.toLowerCase().includes(kw)) return false
      }
      return true
    })
    const total = list.length
    const start = (current - 1) * size
    const records = list.slice(start, start + size)
    return delay(resolve({ records, total, current, size }))
  },

  // 资源详情
  getResource(id) {
    const r = resources.find(x => x.id === Number(id))
    if (!r) return reject('资源不存在', 404)
    return delay(resolve(r))
  },

  // 新增资源
  addResource(data) {
    const id = resources.length + 1
    const r = { id, ...data, status: 1 }
    resources.push(r)
    return delay(resolve(r))
  },

  // 更新资源
  updateResource(data) {
    const idx = resources.findIndex(r => r.id === data.id)
    if (idx === -1) return reject('资源不存在', 404)
    resources[idx] = { ...resources[idx], ...data }
    return delay(resolve(resources[idx]))
  },

  // 删除资源
  deleteResource(id) {
    const idx = resources.findIndex(r => r.id === Number(id))
    if (idx === -1) return reject('资源不存在', 404)
    resources.splice(idx, 1)
    return delay(resolve(null))
  },

  // 切换资源状态
  updateResourceStatus(id, status) {
    const r = resources.find(x => x.id === Number(id))
    if (!r) return reject('资源不存在', 404)
    r.status = Number(status)
    return delay(resolve(r))
  },

  // 创建预约
  createReservation(data) {
    const r = resources.find(x => x.id === data.resourceId)
    if (!r) return reject('资源不存在', 404)
    if (r.status === 3) return reject('该资源正在维修中，暂不可预约', 409)
    if (r.status === 2) return reject('该资源正在被使用', 409)

    // 检查时间冲突
    const newStart = new Date(data.startTime).getTime()
    const newEnd = new Date(data.endTime).getTime()
    const conflict = reservations.find(rv => {
      if (rv.resourceId !== data.resourceId) return false
      if (rv.status === 2) return false // 已取消
      const existStart = new Date(rv.startTime).getTime()
      const existEnd = new Date(rv.endTime).getTime()
      return newStart < existEnd && newEnd > existStart
    })
    if (conflict) return reject('预约时间与已有预约冲突', 409)

    const reservation = {
      id: nextReservationId++,
      userId: 2,
      resourceId: data.resourceId,
      startTime: data.startTime,
      endTime: data.endTime,
      checkinType: data.checkinType || 1,
      checkinFlag: 0,
      remark: data.remark || '',
      status: 1,
      createdAt: new Date().toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-')
    }
    reservations.push(reservation)
    return delay(resolve(reservation))
  },

  // 我的预约
  getMyReservations({ current = 1, size = 10, status } = {}) {
    let list = reservations.filter(r => r.userId === 2)
    if (status) list = list.filter(r => r.status === Number(status))
    list = list.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
    const total = list.length
    const start = (current - 1) * size
    const records = list.slice(start, start + size).map(r => ({
      ...r,
      resource: resources.find(res => res.id === r.resourceId) || {}
    }))
    return delay(resolve({ records, total, current, size }))
  },

  // 预约详情
  getReservation(id) {
    const r = reservations.find(x => x.id === Number(id))
    if (!r) return reject('预约不存在', 404)
    return delay(resolve({ ...r, resource: resources.find(res => res.id === r.resourceId) || {} }))
  },

  // 取消预约
  cancelReservation(id) {
    const r = reservations.find(x => x.id === Number(id))
    if (!r) return reject('预约不存在', 404)
    r.status = 2
    return delay(resolve(r))
  },

  // 打卡
  checkin(id, type) {
    const r = reservations.find(x => x.id === Number(id))
    if (!r) return reject('预约不存在', 404)
    r.checkinFlag = 1
    r.checkinTime = new Date().toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-')
    r.status = 2
    return delay(resolve(r))
  },

  // 统计数据（管理端）
  getStats() {
    const data = {
      resourceTotal: resources.length,
      resourceActive: resources.filter(r => r.status === 1).length,
      resourceRepair: resources.filter(r => r.status === 3).length,
      reservationTotal: reservations.length,
      reservationPending: reservations.filter(r => r.status === 1).length,
      reservationCompleted: reservations.filter(r => r.status === 3).length,
      typeDistribution: [
        { name: '教室', value: resources.filter(r => r.resourceType === 1).length },
        { name: '实验室', value: resources.filter(r => r.resourceType === 2).length },
        { name: '图书馆', value: resources.filter(r => r.resourceType === 3).length },
        { name: '体育馆', value: resources.filter(r => r.resourceType === 4).length }
      ],
      recentReservations: reservations.slice(-5).reverse().map(r => ({
        ...r,
        resourceName: (resources.find(res => res.id === r.resourceId) || {}).name || '未知'
      }))
    }
    return delay(resolve(data))
  }
}
