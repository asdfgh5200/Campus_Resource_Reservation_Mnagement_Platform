import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: () => import('@/layout/MainLayout.vue'),
    redirect: '/home',
    children: [
      {
        path: 'home',
        name: 'Home',
        component: () => import('@/views/user/Home.vue'),
        meta: { title: '资源中心' }
      },
      {
        path: 'category/:type',
        name: 'CategoryDetail',
        component: () => import('@/views/user/CategoryDetail.vue'),
        meta: { title: '资源列表' }
      },
      {
        path: 'resource/:id',
        name: 'ResourceDetail',
        component: () => import('@/views/user/ResourceDetail.vue'),
        meta: { title: '资源详情' }
      },
      {
        path: 'my-reservations',
        name: 'MyReservations',
        component: () => import('@/views/user/MyReservations.vue'),
        meta: { title: '我的预约' }
      },
      {
        path: 'my-messages',
        name: 'MyMessages',
        component: () => import('@/views/user/MyMessages.vue'),
        meta: { title: '消息中心' }
      },
      {
        path: 'profile',
        name: 'MyProfile',
        component: () => import('@/views/user/MyProfile.vue'),
        meta: { title: '我的' }
      },
      // 管理端：数据概览 / 资源管理（资源档案、预约管理、闭馆管理）/ 用户管理
      {
        path: 'admin/dashboard',
        name: 'AdminDashboard',
        component: () => import('@/views/admin/dashboard.vue'),
        meta: { title: '数据概览', admin: true }
      },
      {
        path: 'admin/resources',
        name: 'AdminResources',
        component: () => import('@/views/admin/ResourceManage.vue'),
        meta: { title: '资源档案', admin: true }
      },
      {
        path: 'admin/reservations',
        name: 'AdminReservations',
        component: () => import('@/views/admin/ReservationManage.vue'),
        meta: { title: '预约管理', admin: true }
      },
      {
        path: 'admin/closures',
        name: 'AdminClosures',
        component: () => import('@/views/admin/ClosureManage.vue'),
        meta: { title: '闭馆管理', admin: true }
      },
      {
        path: 'admin/users',
        name: 'AdminUsers',
        component: () => import('@/views/admin/UserManage.vue'),
        meta: { title: '用户管理', admin: true }
      },
      // 旧链接 / 旧书签统一回落（课表管理与报修已并入资源档案）
      { path: 'admin/courses', redirect: '/admin/resources' },
      { path: 'admin/repairs', redirect: '/admin/resources' },
      { path: 'admin/messages', redirect: '/admin/users' }
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
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }
  if (to.meta.admin && !store.isAdmin) {
    next('/home')
    return
  }
  // 管理端侧栏只保留管理入口，管理员误入用户页面时回到数据概览
  if (
    store.isAdmin &&
    (to.path === '/home' || to.path === '/my-reservations' || to.path === '/my-messages')
  ) {
    next('/admin/dashboard')
    return
  }
  next()
})

export default router
