import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  { path: '/login', name: 'Login', component: () => import('@/views/Login.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('@/layout/MainLayout.vue'),
    redirect: '/home',
    children: [
      { path: 'home', name: 'Home', component: () => import('@/views/user/Home.vue'), meta: { title: '资源中心' } },
      { path: 'category/:type', name: 'CategoryDetail', component: () => import('@/views/user/CategoryDetail.vue'), meta: { title: '资源列表' } },
      { path: 'resource/:id', name: 'ResourceDetail', component: () => import('@/views/user/ResourceDetail.vue'), meta: { title: '资源详情' } },
      { path: 'my-reservations', name: 'MyReservations', component: () => import('@/views/user/MyReservations.vue'), meta: { title: '我的预约' } },
      { path: 'admin/dashboard', name: 'AdminDashboard', component: () => import('@/views/admin/Dashboard.vue'), meta: { title: '数据概览', admin: true } },
      { path: 'admin/resources', name: 'AdminResources', component: () => import('@/views/admin/ResourceManage.vue'), meta: { title: '资源管理', admin: true } },
      { path: 'admin/timetable', name: 'AdminTimetable', component: () => import('@/views/admin/TimetableManage.vue'), meta: { title: '课表管理', admin: true } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/home' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const store = useUserStore()
  if (to.meta.public) {
    next()
    return
  }
  if (!store.isLogin) {
    next('/login')
    return
  }
  if (to.meta.admin && !store.isAdmin) {
    next('/home')
    return
  }
  next()
})

export default router
