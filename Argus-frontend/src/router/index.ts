import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // ── 公开页面（无侧边栏布局） ──
    {
      path: '/',
      name: 'home',
      component: () => import('../views/HomeView.vue'),
      meta: { title: '数据洞察中心' },
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { title: '登录 - 数据洞察中心' },
    },

    // ── 认证后的工作区（含侧边栏布局） ──
    {
      path: '/app',
      component: () => import('../layouts/DefaultLayout.vue'),
      redirect: '/app/groups',
      children: [
        {
          path: 'documents',
          name: 'documents',
          component: () => import('../views/documents/DocumentListView.vue'),
          meta: { title: '文档管理' },
        },
        {
          path: 'qa',
          name: 'qa',
          component: () => import('../views/qa/QaView.vue'),
          meta: { title: '知识库问答' },
        },
        {
          path: 'assistant',
          name: 'assistant',
          component: () => import('../views/assistant/AssistantView.vue'),
          meta: { title: 'AI 助手' },
        },
        {
          path: 'assistant/:sessionId',
          name: 'assistant-chat',
          component: () => import('../views/assistant/AssistantView.vue'),
          meta: { title: 'AI 助手' },
        },
        {
          path: 'groups',
          name: 'groups',
          component: () => import('../views/groups/GroupsPage.vue'),
          meta: { title: '协作小组' },
        },
        {
          path: 'admin/users',
          name: 'admin-users',
          component: () => import('../views/admin/UserManagementView.vue'),
          meta: { title: '用户管理', requireAdmin: true },
        },
        {
          path: 'admin/metrics',
          name: 'admin-metrics',
          component: () => import('../views/admin/MetricsView.vue'),
          meta: { title: '使用统计', requireAdmin: true },
        },
        {
          path: 'settings',
          name: 'settings',
          component: () => import('../views/settings/SettingsView.vue'),
          meta: { title: '系统设置' },
        },
      ],
    },

    // ── 404 ──
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('../views/NotFoundView.vue'),
      meta: { title: '页面未找到' },
    },
  ],
  scrollBehavior() {
    return { top: 0 }
  },
})

// 全局标题守卫
router.afterEach((to) => {
  document.title = (to.meta.title as string) || '数据洞察中心'
})

// 全局认证守卫
router.beforeEach(async (to, _from, next) => {
  const authStore = useAuthStore()
  const isPublic = to.path === '/' || to.path === '/login' || to.name === 'not-found'

  // 尝试恢复会话（内部有去重，不会重复请求）
  await authStore.bootstrap()

  // 已登录用户访问公开页面 → 跳转到工作区首页
  if (authStore.isAuthenticated && isPublic) {
    return next(authStore.homePath)
  }

  // 未登录用户访问受保护页面 → 跳转到登录页
  if (!authStore.isAuthenticated && !isPublic) {
    return next('/login')
  }

  // 角色/权限检查
  if (authStore.isAuthenticated && !isPublic) {
    const redirect = authStore.resolveRedirectForPath(to.path)
    if (redirect) {
      return next(redirect)
    }
  }

  next()
})

export default router
