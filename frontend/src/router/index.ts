import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { public: true, title: '注册' },
  },
  {
    path: '/auth/github/callback',
    name: 'github-callback',
    component: () => import('@/views/GitHubCallbackView.vue'),
    meta: { public: true, title: 'GitHub 登录中' },
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: { name: 'home' },
    children: [
      {
        path: '',
        name: 'home',
        component: () => import('@/views/HomePage.vue'),
        meta: { title: 'PR 审查' },
      },
      {
        path: 'review/:id',
        name: 'review-detail',
        component: () => import('@/views/ReviewDetailView.vue'),
        meta: { title: '分析详情' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const userStore = useUserStore()
  if (!to.meta.public && !userStore.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.public && userStore.isLoggedIn && (to.name === 'login' || to.name === 'register')) {
    return { name: 'home' }
  }
  return true
})

router.afterEach((to) => {
  const base = 'PRism'
  document.title = to.meta.title ? `${to.meta.title} · ${base}` : base
})

export default router
