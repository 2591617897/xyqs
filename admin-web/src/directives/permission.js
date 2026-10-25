import { useUserStore } from '@/stores/user'

/**
 * 按钮级权限指令。只负责「显隐」，真正的拦截在后端 @RequirePermission。
 * 两者必须成对出现 —— 指令让人看不见按钮，注解让看得见也调不通。
 */
function check (el, binding) {
  const store = useUserStore()
  if (store.isSuper) return
  const need = Array.isArray(binding.value) ? binding.value : [binding.value]
  const granted = Array.isArray(store.permissions) ? store.permissions : []
  const ok = need.some((p) => granted.includes(p))
  if (!ok && el.parentNode) {
    el.parentNode.removeChild(el)
  }
}

export default {
  mounted: check,
  updated: check
}
