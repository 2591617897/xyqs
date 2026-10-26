import { watch } from 'vue'
import { useUserStore } from '@/stores/user'

/**
 * 按钮级权限指令。只负责「显隐」，真正的拦截在后端 @RequirePermission。
 * 两者必须成对出现 —— 指令让人看不见按钮，注解让看得见也调不通。
 *
 * ⚠️ 两条必须遵守的实现约束：
 *  1) 不能用 removeChild 摘 DOM。`info` 是异步拉的（刷新页面时甚至晚于本组件挂载），
 *     摘掉的节点在权限到达后没有任何办法再挂回来，表现就是「操作列整列空白」。
 *     改为 display:none，并 watch 权限表，权限晚到时自动恢复。
 *  2) 超管的 permissions 后端故意返回空集合（拦截器直接放行），
 *     所以判断必须带上 isSuper，否则超管看后台会一个按钮都没有。
 */
function apply (el, binding) {
  const store = useUserStore()
  const need = Array.isArray(binding.value) ? binding.value : [binding.value]
  const granted = Array.isArray(store.permissions) ? store.permissions : []
  const ok = store.isSuper || need.some((p) => granted.includes(p))
  el.style.display = ok ? '' : 'none'
}

export default {
  mounted (el, binding) {
    // 每个元素挂一个 watcher：登录/刷新/切换账号后 permissions 变了要重算，
    // 否则指令只在挂载那一刻判一次，异步权限永远等不到。
    const store = useUserStore()
    el.__permStop = watch(
      () => [store.isSuper, store.permissions],
      () => apply(el, binding),
      { immediate: true, deep: true }
    )
  },
  updated (el, binding) {
    apply(el, binding)
  },
  unmounted (el) {
    if (el.__permStop) {
      el.__permStop()
      el.__permStop = null
    }
  }
}
