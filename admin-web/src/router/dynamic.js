/**
 * 动态路由：把后端下发的菜单树注册成路由，同时扁平化一份用于面包屑。
 *
 * 菜单表里 component 字段（如 content/PostList）必须和这里 views 下的文件路径一一对应，
 * 对不上就落到 404 兜底组件，不会白屏。
 */
const modules = import.meta.glob('../views/**/*.vue')

function loadView (component) {
  if (!component) return () => import('../views/error/404.vue')
  const key = '../views/' + component + '.vue'
  return modules[key] || (() => import('../views/error/404.vue'))
}

let dynamicAdded = false

export function buildRoutes (menus) {
  const routes = []
  const flat = []
  const walk = (nodes, parentPath) => {
    ;(nodes || []).forEach((node) => {
      const fullPath = (node.path || '').startsWith('/')
        ? node.path
        : (parentPath ? parentPath + '/' + (node.path || '') : (node.path || ''))
      flat.push({ title: node.name, path: fullPath })
      if (node.children && node.children.length) {
        walk(node.children, fullPath)
      } else if (node.path) {
        routes.push({
          path: fullPath.replace(/^\//, ''),
          name: String(node.id),
          component: loadView(node.component),
          meta: { title: node.name, icon: node.icon, keepAlive: false }
        })
      }
    })
  }
  walk(menus, '')
  return { routes, flat }
}

export function addDynamicRoutes (router, menus) {
  if (dynamicAdded) return { flat: [] }
  const { routes, flat } = buildRoutes(menus)
  routes.forEach((r) => router.addRoute('Layout', r))
  router.addRoute({ path: '/:pathMatch(.*)*', name: 'NotFoundTail', component: () => import('../views/error/404.vue') })
  dynamicAdded = true
  return { flat }
}

export function resetDynamic () {
  dynamicAdded = false
}
