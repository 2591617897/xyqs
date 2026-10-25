<template>
  <span class="count-up">{{ display }}</span>
</template>

<script setup>
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  value: { type: [Number, String], default: 0 },
  duration: { type: Number, default: 900 },
  separator: { type: Boolean, default: true }
})

const display = ref(format(0))
let current = 0
let raf = 0

function format (n) {
  const v = Math.round(n)
  return props.separator ? v.toLocaleString('zh-CN') : String(v)
}

function animate (target) {
  cancelAnimationFrame(raf)
  const from = current
  const delta = target - from
  if (!delta) {
    current = target
    display.value = format(target)
    return
  }
  const start = performance.now()
  const dur = Math.max(120, props.duration)
  const step = (now) => {
    const t = Math.min(1, (now - start) / dur)
    const eased = 1 - Math.pow(1 - t, 3) // easeOutCubic
    current = from + delta * eased
    display.value = format(current)
    if (t < 1) raf = requestAnimationFrame(step)
    else {
      current = target
      display.value = format(target)
    }
  }
  raf = requestAnimationFrame(step)
}

watch(() => props.value, (v) => animate(Number(v) || 0))
onMounted(() => animate(Number(props.value) || 0))
onBeforeUnmount(() => cancelAnimationFrame(raf))
</script>
