<script setup lang="ts">
defineProps<{
  groupName: string
  hasGroup: boolean
}>()

const emit = defineEmits<{
  pick: [prompt: string]
}>()

const starters = [
  {
    title: '全局概览',
    prompt: '请基于知识库内容，总结最重要的 3 个主题，并分别列出关键要点。',
    icon: 'globe',
  },
  {
    title: '定义查询',
    prompt: '请解释一下「XXX」在这个知识库里是如何定义的？相关文档里是怎么描述的？',
    icon: 'book',
  },
  {
    title: '对比分析',
    prompt: '请比较知识库中涉及到的两种方案/概念的异同，并在结尾给出推荐选择。',
    icon: 'compare',
  },
  {
    title: '步骤指引',
    prompt: '根据知识库提供的资料，给出完成某件事的标准步骤清单，并标注每一步的关键注意点。',
    icon: 'steps',
  },
]
</script>

<template>
  <div class="qa-hero">
    <!-- Decorative mesh background -->
    <div class="qa-hero__mesh" aria-hidden="true">
      <div class="qa-hero__blob qa-hero__blob--a" />
      <div class="qa-hero__blob qa-hero__blob--b" />
      <div class="qa-hero__grid" />
    </div>

    <div class="qa-hero__inner">
      <!-- Eyebrow glyph -->
      <div class="qa-hero__mark">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="10" />
          <path d="M8 14s1.5 2 4 2 4-2 4-2" />
          <line x1="9" y1="9" x2="9.01" y2="9" />
          <line x1="15" y1="9" x2="15.01" y2="9" />
        </svg>
      </div>

      <p class="qa-hero__eyebrow">Knowledge Q&amp;A · Argus</p>

      <h1 class="qa-hero__title">
        向<span class="qa-hero__title-accent">你的知识库</span>提问
      </h1>

      <p class="qa-hero__subtitle">
        检索 · 理解 · 溯源 — 每一个回答都附带可追踪的文档引用，让你一眼看清答案的出处。
      </p>

      <!-- Current context badge -->
      <div v-if="hasGroup" class="qa-hero__context">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <ellipse cx="12" cy="5" rx="9" ry="3" />
          <path d="M3 5v7c0 1.7 4 3 9 3s9-1.3 9-3V5" />
          <path d="M3 12v7c0 1.7 4 3 9 3s9-1.3 9-3v-7" />
        </svg>
        <span>当前知识库：<strong>{{ groupName }}</strong></span>
      </div>
      <div v-else class="qa-hero__context qa-hero__context--warn">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="10" />
          <line x1="12" y1="8" x2="12" y2="12" />
          <line x1="12" y1="16" x2="12.01" y2="16" />
        </svg>
        <span>请先在左侧选择一个群组作为知识库范围</span>
      </div>

      <!-- Starter prompts -->
      <div class="qa-hero__starters">
        <button
          v-for="(item, i) in starters"
          :key="item.title"
          class="qa-hero__starter"
          type="button"
          :disabled="!hasGroup"
          :style="{ animationDelay: `${0.08 * i + 0.1}s` }"
          @click="emit('pick', item.prompt)"
        >
          <div class="qa-hero__starter-icon" :class="`qa-hero__starter-icon--${item.icon}`">
            <template v-if="item.icon === 'globe'">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="10" />
                <line x1="2" y1="12" x2="22" y2="12" />
                <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z" />
              </svg>
            </template>
            <template v-else-if="item.icon === 'book'">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
                <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
              </svg>
            </template>
            <template v-else-if="item.icon === 'compare'">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M16 3h5v5" />
                <path d="M4 20L21 3" />
                <path d="M21 16v5h-5" />
                <path d="M15 15l6 6" />
                <path d="M4 4l5 5" />
              </svg>
            </template>
            <template v-else>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="9 11 12 14 22 4" />
                <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11" />
              </svg>
            </template>
          </div>
          <div class="qa-hero__starter-body">
            <span class="qa-hero__starter-title">{{ item.title }}</span>
            <span class="qa-hero__starter-prompt">{{ item.prompt }}</span>
          </div>
          <svg class="qa-hero__starter-arrow" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="5" y1="12" x2="19" y2="12" />
            <polyline points="12 5 19 12 12 19" />
          </svg>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.qa-hero {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 32px;
  position: relative;
  overflow: hidden;
  background: #fff;
}

/* Mesh background */
.qa-hero__mesh {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}

.qa-hero__blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  opacity: 0.4;
}

.qa-hero__blob--a {
  top: -80px;
  left: 10%;
  width: 360px;
  height: 360px;
  background: radial-gradient(circle, rgba(74, 144, 217, 0.4) 0%, rgba(74, 144, 217, 0) 70%);
}

.qa-hero__blob--b {
  bottom: -100px;
  right: 10%;
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, rgba(92, 201, 193, 0.35) 0%, rgba(92, 201, 193, 0) 70%);
}

.qa-hero__grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(74, 144, 217, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(74, 144, 217, 0.04) 1px, transparent 1px);
  background-size: 32px 32px;
  mask-image: radial-gradient(ellipse 70% 60% at 50% 50%, #000 30%, transparent 80%);
  -webkit-mask-image: radial-gradient(ellipse 70% 60% at 50% 50%, #000 30%, transparent 80%);
}

/* Inner container */
.qa-hero__inner {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 760px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  animation: hero-fade 0.6s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes hero-fade {
  from { opacity: 0; transform: translateY(12px); }
  to   { opacity: 1; transform: translateY(0); }
}

.qa-hero__mark {
  width: 60px;
  height: 60px;
  border-radius: 18px;
  background: linear-gradient(135deg, var(--brand-primary), var(--brand-accent));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
  box-shadow:
    0 10px 28px rgba(74, 144, 217, 0.3),
    0 0 0 8px rgba(74, 144, 217, 0.06);
}

.qa-hero__mark svg {
  width: 28px;
  height: 28px;
}

.qa-hero__eyebrow {
  margin: 0 0 10px;
  font-family: 'Poppins', sans-serif;
  font-size: 0.72rem;
  font-weight: 600;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--brand-primary);
}

.qa-hero__title {
  margin: 0 0 14px;
  font-family: 'Poppins', 'Noto Sans SC', sans-serif;
  font-size: 2.4rem;
  font-weight: 800;
  line-height: 1.1;
  color: var(--text-primary);
  letter-spacing: -0.03em;
}

.qa-hero__title-accent {
  background: linear-gradient(135deg, var(--brand-primary), var(--brand-accent));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.qa-hero__subtitle {
  margin: 0 0 22px;
  font-size: 0.95rem;
  line-height: 1.65;
  color: var(--text-secondary);
  max-width: 560px;
}

/* Context */
.qa-hero__context {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 6px 14px;
  margin-bottom: 36px;
  background: rgba(74, 144, 217, 0.08);
  border: 1px solid rgba(74, 144, 217, 0.2);
  border-radius: 100px;
  font-size: 0.82rem;
  color: var(--text-secondary);
}

.qa-hero__context svg {
  color: var(--brand-primary);
}

.qa-hero__context strong {
  font-weight: 600;
  color: var(--text-primary);
}

.qa-hero__context--warn {
  background: rgba(245, 158, 11, 0.08);
  border-color: rgba(245, 158, 11, 0.25);
}

.qa-hero__context--warn svg {
  color: #b45309;
}

/* Starter cards */
.qa-hero__starters {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  width: 100%;
  max-width: 680px;
}

.qa-hero__starter {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 16px;
  background: #fff;
  border: 1px solid var(--border-default);
  border-radius: 14px;
  text-align: left;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
  opacity: 0;
  animation: starter-in 0.5s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}

@keyframes starter-in {
  from { opacity: 0; transform: translateY(12px); }
  to   { opacity: 1; transform: translateY(0); }
}

.qa-hero__starter:hover:not(:disabled) {
  transform: translateY(-2px);
  border-color: var(--brand-primary);
  box-shadow: 0 10px 24px rgba(74, 144, 217, 0.1);
}

.qa-hero__starter:hover:not(:disabled) .qa-hero__starter-arrow {
  color: var(--brand-primary);
  transform: translateX(2px);
}

.qa-hero__starter:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.qa-hero__starter-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 9px;
  flex-shrink: 0;
  background: rgba(74, 144, 217, 0.1);
  color: var(--brand-primary);
}

.qa-hero__starter-icon svg {
  width: 17px;
  height: 17px;
}

.qa-hero__starter-icon--book {
  background: rgba(92, 201, 193, 0.1);
  color: var(--brand-accent-dark);
}

.qa-hero__starter-icon--compare {
  background: rgba(139, 92, 246, 0.1);
  color: #7c3aed;
}

.qa-hero__starter-icon--steps {
  background: rgba(16, 185, 129, 0.1);
  color: #059669;
}

.qa-hero__starter-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.qa-hero__starter-title {
  font-family: 'Poppins', 'Noto Sans SC', sans-serif;
  font-size: 0.86rem;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.005em;
}

.qa-hero__starter-prompt {
  font-size: 0.76rem;
  color: var(--text-secondary);
  line-height: 1.45;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.qa-hero__starter-arrow {
  flex-shrink: 0;
  color: var(--text-muted);
  margin-top: 4px;
  transition: all 0.2s ease;
}

@media (max-width: 720px) {
  .qa-hero__title {
    font-size: 1.8rem;
  }

  .qa-hero__starters {
    grid-template-columns: 1fr;
  }
}
</style>
