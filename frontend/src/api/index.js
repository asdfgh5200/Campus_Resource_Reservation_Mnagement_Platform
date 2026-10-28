/**
 * 真实后端 API 层 —— 与原 mockApi 同名同签名，视图层零改动切换
 * 后端统一返回 { code, message, data }，本层直接透传给调用方
 */
import axios from 'axios'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// 请求拦截：携带 token
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：透传 { code, message, data }；401 统一踢回登录页
request.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const res = error.response?.data || { code: 500, message: '网络异常，请稍后重试', data: null }
    if (res.code === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      if (!location.pathname.startsWith('/login')) {
        location.href = '/login'
      }
    }
    return res
  }
)

export const api = {
  // 登录
  login(username, password) {
    return request.post('/auth/login', { username, password })
  },

  // 注册
  register(data) {
    return request.post('/auth/register', data)
  },

  // 当前用户信息
  getUserInfo() {
    return request.get('/users/me')
  },

  // 更新个人信息
  updateUserInfo(data) {
    return request.put('/users/me', data)
  },

  // 资源分页查询
  getResourcePage({
    current = 1,
    size = 10,
    type,
    keyword,
    status,
    campus,
    building,
    floor,
    roomType
  } = {}) {
    return request.get('/resources/page', {
      params: { current, size, type, keyword, status, campus, building, floor, roomType }
    })
  },

  // 按类型统计各校区楼栋与真实楼层/教室类型（筛选器级联数据）
  getBuildings(type) {
    return request.get('/resources/buildings', { params: { type } })
  },

  // 资源详情
  getResource(id) {
    return request.get(`/resources/${id}`)
  },

  // 新增资源
  addResource(data) {
    return request.post('/resources', data)
  },

  // 更新资源
  updateResource(data) {
    return request.put('/resources', data)
  },

  // 删除资源
  deleteResource(id) {
    return request.delete(`/resources/${id}`)
  },

  // 切换资源状态
  updateResourceStatus(id, status) {
    return request.put(`/resources/${id}/status/${status}`)
  },

  // 创建预约
  createReservation(data) {
    return request.post('/reservations', data)
  },

  // 我的预约（date 为可选单日，格式 YYYY-MM-DD）
  getMyReservations({ current = 1, size = 10, status, date } = {}) {
    return request.get('/reservations/my', {
      params: { current, size, status, date }
    })
  },

  // 预约详情
  getReservation(id) {
    return request.get(`/reservations/${id}`)
  },

  // 单条预约的「当时情况」：预约 + 操作时间线 + 关联违规
  getReservationTrace(id) {
    return request.get(`/reservations/${id}/trace`)
  },

  // 某资源某日已占用时段（时段条标红用）
  getReservationsByResource(resourceId, date) {
    return request.get('/reservations/resource', { params: { resourceId, date } })
  },

  // 取消预约
  cancelReservation(id) {
    return request.post(`/reservations/${id}/cancel`)
  },

  // 删除预约记录（仅限已结束的预约）
  deleteReservation(id) {
    return request.delete(`/reservations/${id}`)
  },

  // 提前释放空间
  releaseReservation(id) {
    return request.post(`/reservations/${id}/release`)
  },

  // 验证码打卡
  checkinByCode(data) {
    return request.post('/checkins/verify', data)
  },

  // 浏览器定位打卡；后端仅保存校验结论与距离，不保存原始坐标
  checkinByLocation(data) {
    return request.post('/checkins/location', data)
  },

  // 资源当日验证码（管理员）
  getCheckinCode(resourceId) {
    return request.get(`/checkins/code/${resourceId}`)
  },

  // 附近资源推荐
  getNearby({ lat, lng, type, radius }) {
    return request.get('/location/nearby', { params: { lat, lng, type, radius } })
  },

  // 课表导入 / 空闲时段
  importCourses(list) {
    return request.post('/courses/import', list)
  },
  getAvailableSlots(roomId, date) {
    return request.get('/courses/available', { params: { roomId, date } })
  },

  // 空闲资源查询：按类型 + 校区 + 楼栋 + 细分类型 + 日期 + 时段 + 关键词筛选真正可用的资源
  // type: 1 教室 / 2 实验室 / 3 图书馆 / 4 体育馆（不传默认 1）
  getFreeRooms({ type, date, start, end, campus, buildings, floor, roomTypes, keyword } = {}) {
    return request.get('/courses/free-rooms', {
      params: {
        type,
        date,
        start,
        end,
        campus,
        floor,
        keyword,
        // 后端 List<String> 接受逗号分隔，避开 axios 默认的 [] 下标序列化
        building: buildings && buildings.length ? buildings.join(',') : undefined,
        roomType: roomTypes && roomTypes.length ? roomTypes.join(',') : undefined
      }
    })
  },

  // 管理端：课表列表 / 删除
  getCourses(params) {
    return request.get('/courses', { params })
  },
  deleteCourse(id) {
    return request.delete(`/courses/${id}`)
  },

  // 违规记录
  getViolations(params) {
    return request.get('/violations', { params })
  },

  // 我的违规概览（近 7 天次数 / 累计次数 / 是否被限制预约）
  getMyViolationSummary() {
    return request.get('/violations/summary')
  },

  // 维修工单
  createRepair(data) {
    return request.post('/repairs', data)
  },
  // 我的维修工单（用户端消息中心）
  getMyRepairs() {
    return request.get('/repairs/mine')
  },
  getRepairs(params) {
    return request.get('/repairs', { params })
  },
  handleRepair(id, data) {
    return request.put(`/repairs/${id}/handle`, data)
  },

  // 节假日
  getHolidays() {
    return request.get('/holidays')
  },
  addHoliday(data) {
    return request.post('/holidays', data)
  },
  deleteHoliday(id) {
    return request.delete(`/holidays/${id}`)
  },

  // 管理端统计
  getStats() {
    return request.get('/stats/dashboard')
  },

  // 管理端：全部预约（含用户名）
  getAdminReservations(params) {
    return request.get('/admin/reservations/page', { params })
  },

  // 管理端：预约操作日志（可按预约用户/操作类型/操作人角色/所属预约筛选）
  getAdminReservationLogs(params) {
    return request.get('/admin/reservations/logs', { params })
  },

  // 管理端：单条预约详情（含用户、资源、打卡坐标与当日验证码）
  getAdminReservationDetail(id) {
    return request.get(`/admin/reservations/${id}`)
  },

  // 管理端：按用户聚合的违规情况（支持关键词搜索）
  getAdminViolationUsers(params) {
    return request.get('/admin/violations/users', { params })
  },

  // 管理端：用户列表（仅普通用户，含预约状态/取消次数/违规统计）与用户详情
  getAdminUsers(params) {
    return request.get('/admin/users', { params })
  },
  getAdminUserDetail(id, params) {
    return request.get(`/admin/users/${id}`, { params })
  },
  updateAdminUser(id, data) {
    return request.put(`/admin/users/${id}`, data)
  },
  notifyAdminUser(id, content) {
    return request.post(`/admin/users/${id}/notify`, { content })
  },
  updateAdminViolationStatus(id, status) {
    return request.put(`/admin/violations/${id}/status`, { status })
  },
  updateAdminReservationStatus(id, status) {
    return request.put(`/admin/reservations/${id}/status`, { status })
  },

  // 系统全局状态：一键闭馆横幅与预约页拦截使用
  getCampusStatus() {
    return request.get('/system/campus-status')
  },
  campusClose(reason) {
    return request.post('/system/campus-close', reason ? { reason } : {})
  },
  campusOpen() {
    return request.post('/system/campus-open')
  },

  // 细粒度类型闭馆
  getClosures() {
    return request.get('/system/closures')
  },
  addClosure(data) {
    return request.post('/system/closures', data)
  },
  deleteClosure(id) {
    return request.delete(`/system/closures/${id}`)
  },

  // 站内消息
  askAdmin(content) {
    return request.post('/messages/ask', { content })
  },
  getMyMessages() {
    return request.get('/messages/mine')
  },
  getMyUnreadCount() {
    return request.get('/messages/mine/unread')
  },
  markMessageRead(id) {
    return request.put(`/messages/${id}/read`)
  },
  getAdminMessages(params) {
    return request.get('/messages/admin/page', { params })
  },
  getAdminUnreadCount() {
    return request.get('/messages/admin/unread')
  },
  replyMessage(askId, content) {
    return request.post(`/messages/admin/reply/${askId}`, { content })
  },
  broadcastMessage(content, scope) {
    return request.post('/messages/admin/broadcast', { content, scope })
  },

  // 智约 AI 机器人
  aiChat(messages) {
    return request.post('/ai/chat', { messages })
  }
}

export default api
