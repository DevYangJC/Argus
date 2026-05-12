<script setup lang="ts">
defineProps<{
  readyCount: number
  processingCount: number
  failedCount: number
  totalSize: string
  failedHint: string
}>()
</script>

<template>
  <div class="doc-stats">
    <!-- Ready -->
    <div class="doc-stats__card doc-stats__card--ready">
      <div class="doc-stats__top">
        <span class="doc-stats__label">已就绪</span>
        <div class="doc-stats__icon doc-stats__icon--green">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
            <polyline points="22 4 12 14.01 9 11.01"/>
          </svg>
        </div>
      </div>
      <div class="doc-stats__num-row">
        <span class="doc-stats__num">{{ readyCount }}</span>
      </div>
      <p class="doc-stats__hint">可直接预览与问答使用</p>
    </div>

    <!-- Processing -->
    <div class="doc-stats__card doc-stats__card--processing">
      <div class="doc-stats__top">
        <span class="doc-stats__label">
          处理中
          <span v-if="processingCount > 0" class="doc-stats__dot" />
        </span>
        <div class="doc-stats__icon doc-stats__icon--amber">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10"/>
            <polyline points="12 6 12 12 16 14"/>
          </svg>
        </div>
      </div>
      <div class="doc-stats__num-row">
        <span class="doc-stats__num">{{ processingCount }}</span>
      </div>
      <p class="doc-stats__hint">仍在切分、向量化或排队中</p>
    </div>

    <!-- Failed -->
    <div class="doc-stats__card" :class="failedCount > 0 ? 'doc-stats__card--failed' : ''">
      <div class="doc-stats__top">
        <span class="doc-stats__label">异常文件</span>
        <div class="doc-stats__icon doc-stats__icon--red">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
            <line x1="12" y1="9" x2="12" y2="13"/>
            <line x1="12" y1="17" x2="12.01" y2="17"/>
          </svg>
        </div>
      </div>
      <div class="doc-stats__num-row">
        <span class="doc-stats__num">{{ failedCount }}</span>
      </div>
      <p class="doc-stats__hint">{{ failedHint }}</p>
    </div>

    <!-- Total Size -->
    <div class="doc-stats__card">
      <div class="doc-stats__top">
        <span class="doc-stats__label">当前体积</span>
        <div class="doc-stats__icon doc-stats__icon--blue">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M22 12h-4l-3 9L9 3l-3 9H2"/>
          </svg>
        </div>
      </div>
      <div class="doc-stats__num-row">
        <span class="doc-stats__num doc-stats__num--text">{{ totalSize }}</span>
      </div>
      <p class="doc-stats__hint">按当前筛选结果累计大小</p>
    </div>
  </div>
</template>

<style scoped>
.doc-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 22px;
}

.doc-stats__card {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 20px 22px 18px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  background: var(--surface-white);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
  overflow: hidden;
}

.doc-stats__card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, transparent, transparent);
  transition: background 0.25s ease;
}

.doc-stats__card:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.06);
}

.doc-stats__card--ready::before {
  background: linear-gradient(90deg, #10b981, #5cc9c1);
}

.doc-stats__card--processing::before {
  background: linear-gradient(90deg, #f59e0b, #fbbf24);
}

.doc-stats__card--failed::before {
  background: linear-gradient(90deg, #ef4444, #f97316);
}

.doc-stats__card:not(.doc-stats__card--ready):not(.doc-stats__card--processing):not(.doc-stats__card--failed)::before {
  background: linear-gradient(90deg, var(--brand-primary), var(--brand-accent));
}

.doc-stats__top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.doc-stats__label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--text-secondary);
  letter-spacing: 0.01em;
}

.doc-stats__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
}

.doc-stats__icon svg {
  width: 18px;
  height: 18px;
}

.doc-stats__icon--green {
  background: rgba(16, 185, 129, 0.1);
  color: #10b981;
}

.doc-stats__icon--amber {
  background: rgba(245, 158, 11, 0.1);
  color: #f59e0b;
}

.doc-stats__icon--red {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}

.doc-stats__icon--blue {
  background: rgba(74, 144, 217, 0.1);
  color: var(--brand-primary);
}

.doc-stats__num-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 8px;
}

.doc-stats__num {
  font-family: 'Poppins', 'JetBrains Mono', sans-serif;
  font-size: 2.15rem;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1;
  letter-spacing: -0.02em;
}

.doc-stats__num--text {
  font-size: 1.55rem;
  letter-spacing: -0.01em;
}

.doc-stats__hint {
  margin: 0;
  font-size: 0.78rem;
  color: var(--text-muted);
  line-height: 1.5;
}

.doc-stats__dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--el-color-warning);
  animation: pulse-dot 1.8s ease-in-out infinite;
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(0.85); }
}

@media (max-width: 1100px) {
  .doc-stats {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 560px) {
  .doc-stats {
    grid-template-columns: 1fr;
  }
}
</style>
