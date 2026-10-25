<template>
  <div class="stat-card" :class="'tone-' + tone" :style="{ animationDelay: delay + 'ms' }">
    <span class="stat-glow" aria-hidden="true" />
    <div class="stat-top">
      <span class="stat-label">{{ label }}</span>
      <span class="stat-icon">
        <el-icon :size="15"><component :is="icon" /></el-icon>
      </span>
    </div>
    <div class="stat-value">
      <CountUp :value="value" />
    </div>
    <div class="stat-foot">
      <template v-if="today !== undefined && today !== null">
        <span class="stat-today">今日 <b>+{{ today }}</b></span>
      </template>
      <span v-else class="stat-muted">累计</span>
    </div>
  </div>
</template>

<script setup>
import CountUp from './CountUp.vue'

defineProps({
  label: { type: String, default: '' },
  value: { type: [Number, String], default: 0 },
  today: { type: [Number, String], default: undefined },
  icon: { type: [String, Object], default: 'DataLine' },
  tone: { type: String, default: 'brand' },
  delay: { type: Number, default: 0 }
})
</script>

<style scoped>
.stat-card {
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 16px 16px 14px;
  background: #fff;
  border: 1px solid var(--line-soft);
  border-radius: var(--r-lg);
  box-shadow: var(--shadow-1);
  cursor: default;
  animation: statIn .5s var(--ease) both;
  transition: transform var(--dur) var(--ease), box-shadow var(--dur) var(--ease),
    border-color var(--dur) var(--ease);
}

.stat-card:hover {
  transform: translateY(-3px);
  border-color: var(--tone-line);
  box-shadow: var(--shadow-2);
}

/* 右下角彩色光斑，hover 时放大 */
.stat-glow {
  position: absolute;
  right: -34px;
  bottom: -46px;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  background: radial-gradient(circle, var(--tone-soft) 0%, transparent 70%);
  opacity: .85;
  transition: transform .5s var(--ease), opacity .5s var(--ease);
  pointer-events: none;
}

.stat-card:hover .stat-glow {
  transform: scale(1.35);
  opacity: 1;
}

.stat-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  position: relative;
}

.stat-label {
  font-size: 12.5px;
  color: var(--text-2);
  letter-spacing: .2px;
}

.stat-icon {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 9px;
  color: var(--tone);
  background: var(--tone-soft);
  box-shadow: inset 0 0 0 1px var(--tone-line);
  transition: transform var(--dur) var(--ease), background var(--dur);
}

.stat-card:hover .stat-icon {
  transform: rotate(-8deg) scale(1.08);
  background: var(--tone);
  color: #fff;
}

.stat-value {
  position: relative;
  font-size: 27px;
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: -.5px;
  color: var(--text-1);
  font-variant-numeric: tabular-nums;
}

.stat-foot {
  position: relative;
  font-size: 12px;
  color: var(--text-3);
  min-height: 18px;
}

.stat-today b {
  color: var(--tone);
  font-weight: 600;
}

.stat-muted {
  color: var(--text-3);
}

/* ---------- 色调 ---------- */
.tone-brand {
  --tone: #7C63D8;
  --tone-soft: #F1EDFD;
  --tone-line: #D9D0F7;
}
.tone-sky {
  --tone: #3B7BF0;
  --tone-soft: #E9F0FE;
  --tone-line: #C6D9FB;
}
.tone-violet {
  --tone: #6366F1;
  --tone-soft: #ECEDFE;
  --tone-line: #CFD2FB;
}
.tone-amber {
  --tone: #D97706;
  --tone-soft: #FEF3E2;
  --tone-line: #F8DCB4;
}
.tone-teal {
  --tone: #0891B2;
  --tone-soft: #E4F6FA;
  --tone-line: #BCE3EE;
}
.tone-rose {
  --tone: #E4568B;
  --tone-soft: #FDE8F0;
  --tone-line: #F8C5D8;
}

@keyframes statIn {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
</style>
