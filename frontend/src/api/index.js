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
request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：透传 { code, message, data }；401 统一踢回登录页
request.interceptors.response.use(
  response => response.data,
  error => {
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
  getResourcePage({ current = 1, size = 10, type, keyword, status, building, floor } = {}) {
    return request.get('/resources/page', {
      params: { current, size, type, keyword, status, building, floor }
    })
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

  // 我的预约
  getMyReservations({ current = 1, size = 10, status } = {}) {
    return request.get('/reservations/my', {
      params: { current, size, status }
    })
  },

  // 预约详情
  getReservation(id) {
    return request.get(`/reservations/${id}`)
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

  // 暂离（minutes: 30 普通 / 60 仅饭点）
  awayReservation(id, minutes) {
    return request.post(`/reservations/${id}/away`, null, { params: { minutes } })
  },

  // 结束暂离
  cancelAway(id) {
    return request.post(`/reservations/${id}/away/cancel`)
  },

  // 验证码打卡
  checkinByCode(data) {
    return request.post('/checkins/verify', data)
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

  // 维修工单
  createRepair(data) {
    return request.post('/repairs', data)
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

  // 管理端：预约操作日志
  getAdminReservationLogs(params) {
    return request.get('/admin/reservations/logs', { params })
  }
}

export default api
