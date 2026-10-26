import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { addDynamicRoutes } from './dynamic'

/**
 * 只放静态路由：登录、根布局、错误页。
 * 其余菜单都由后端下发的菜单树在登录后 addRoute 进去（见 router/dynamic.js）。
 */
const routes = [
  { path: '/login', name: 'Login', component: () => import('@/views/login/index.vue'), meta: { title: '登录' } },
  {
    path: '/',
    name: 'Layout',
    component: () => import('@/layout/index.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/dashboard/index.vue'), meta: { title: '数据看板' } }
    ]
  },
  { path: '/403', name: 'Forbidden', component: () => import('@/views/error/403.vue'), meta: { title: '无权限' } },
  { path: '/:pathMatch(.*)*', name: 'NotFound', component: () => import('@/views/error/404.vue'), meta: { title: '页面不存在' } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 刷新/直接打开动态页时，首帧会命中顶层 404（此时 Layout 未挂载、动态路由没注册，
 * 布局里的 ensureMenus 根本没机会跑）。这里在全局守卫里补注册一次再重定向重解析。
 * 每个路径只补一次，避免「组件路径配错 → 404 → 再注册」死循环。
 */
const retriedPaths = new Set()

router.beforeEach(async (to) => {
  if (to.name !== 'NotFound' || retriedPaths.has(to.fullPath)) return true
  const userStore = useUserStore()
  if (!userStore.token) return true
  retriedPaths.add(to.fullPath)
  try {
    // ⚠️ 必须连 info 一起拉：只拉菜单会让 store.info 保持 null，
    // 于是顶栏显示「未登录」、isSuper=false、permissions=[]，
    // 页面上所有 v-permission 按钮（比如举报工单的操作列）全部被隐藏。
    // 而 Layout 的 ensureMenus 看到 menus 已有数据就跳过 loadInfo，把这个问题固化下来。
    await userStore.loadInfo().catch(() => {})
    await userStore.loadMenus()
    addDynamicRoutes(router, userStore.menus)
    return { path: to.path, query: to.query, hash: to.hash, replace: true }
  } catch (e) {
    // 拉菜单失败（token 过期等）按原样落到 404；request 拦截器会另行引导重新登录
    return true
  }
})

export default router
