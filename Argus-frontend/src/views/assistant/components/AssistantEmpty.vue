<script setup lang="ts">
import type { AssistantToolMode } from '@/types/assistant'

defineProps<{
  mode: AssistantToolMode
  hasKb: boolean
}>()

const emit = defineEmits<{
  pick: [prompt: string, mode: AssistantToolMode]
}>()

const chatStarters = [
  { title: '头脑风暴', prompt: '帮我围绕 XXX 展开 5 个不同角度的思考。', icon: 'spark' },
  { title: '解释概念', prompt: '用通俗的方式给我讲解 XXX，最好加一个生活化的比喻。', icon: 'book' },
  { title: '代码审查', prompt: '下面这段代码有什么可以优化的地方？\n\n```\n// 粘贴代码\n```', icon: 'code' },
  { title: '总结对比', prompt: '帮我对比 A 和 B 两种方案在成本、性能、可维护性上的差异。', icon: 'compare' },
]

const kbStarters = [
  { title: '知识库概览', prompt: '请基于当前知识库，列出最重要的 3 个主题并给出简要介绍。', icon: 'globe' },
  { title: '定义查询', prompt: '请解释一下「XXX」在当前知识库中是如何被定义和描述的？', icon: 'book' },
  { title: '步骤指引', prompt: '根据知识库，给出完成「XXX 任务」的完整步骤与注意事项。', icon: 'steps' },
  { title: '源头追溯', prompt: '关于「XXX」这个结论，知识库里有哪些文档支撑？请列出证据。', icon: 'link' },
]
</script>

<template>
  <div class="aempty">
    <!-- Backdrop -->
    <div class="aempty__mesh" aria-hidden="true">
      <div class="aempty__blob aempty__blob--a" />
      <div class="aempty__blob aempty__blob--b" />
      <div class="aempty__grid" />
    </div>

    <div class="aempty__inner">
      <!-- Mark -->
      <div class="aempty__mark" :class="{ 'is-kb': mode === 'KB_SEARCH' }">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
          <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
        </svg>
        <span class="aempty__mark-orbit aempty__mark-orbit--1" />
        <span class="aempty__mark-orbit aempty__mark-orbit--2" />
      </div>

      <p class="aempty__eyebrow">
        {{ mode === 'CHAT' ? 'Conversational Agent · Argus' : 'Knowledge-Base Retrieval · Argus' }}
      </p>

      <h1 class="aempty__title">
        <span class="aempty__title-line">开启一次</span>
        <span class="aempty__title-line aempty__title-line--accent" :class="{ 'is-kb': mode === 'KB_SEARCH' }">
          {{ mode === 'CHAT' ? '自由对话' : '知识库检索' }}
        </span>
      </h1>

      <p class="aempty__subtitle">
        <template v-if="mode === 'CHAT'">
          让 Argus Agent 陪你头脑风暴、解释概念、审代码、做对比 — 所有回答都会保存到会话历史里。
        </template>
        <template v-else>
          在指定知识库范围内提问，Agent 会先检索相关文档，再给出带引用溯源的答案。
          <span v-if="!hasKb" class="aempty__subtitle-warn">请先在下方输入框选择一个知识库。</span>
        </template>
      </p>

      <!-- Starters -->
      <div class="aempty__starters">
        <button
          v-for="(item, i) in (mode === 'CHAT' ? chatStarters : kbStarters)"
          :key="`${mode}-${item.title}`"
          class="aempty__starter"
          :class="{ 'is-kb': mode === 'KB_SEARCH' }"
          type="button"
          :disabled="mode === 'KB_SEARCH' && !hasKb"
          :style="{ animationDelay: `${0.08 * i + 0.1}s` }"
          @click="emit('pick', item.prompt, mode)"
        >
          <div class="aempty__starter-icon">
            <template v-if="item.icon === 'spark'">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 2v6" /><path d="M12 16v6" /><path d="M2 12h6" /><path d="M16 12h6" />
                <path d="M4.93 4.93l4.24 4.24" /><path d="M14.83 14.83l4.24 4.24" />
                <path d="M14.83 9.17l4.24-4.24" /><path d="M4.93 19.07l4.24-4.24" />
              </svg>
            </template>
            <template v-else-if="item.icon === 'book'">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
                <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
              </svg>
            </template>
            <template v-else-if="item.icon === 'code'">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="16 18 22 12 16 6" />
                <polyline points="8 6 2 12 8 18" />
              </svg>
            </template>
            <template v-else-if="item.icon === 'compare'">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M16 3h5v5" /><path d="M4 20L21 3" />
                <path d="M21 16v5h-5" /><path d="M15 15l6 6" /><path d="M4 4l5 5" />
              </svg>
            </template>
            <template v-else-if="item.icon === 'globe'">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="10" />
                <line x1="2" y1="12" x2="22" y2="12" />
                <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z" />
              </svg>
            </template>
            <template v-else-if="item.icon === 'steps'">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="9 11 12 14 22 4" />
                <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11" />
              </svg>
            </template>
            <template v-else>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" />
                <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" />
              </svg>
            </template>
          </div>
          <div class="aempty__starter-body">
            <span class="aempty__starter-title">{{ item.title }}</span>
            <span class="aempty__starter-prompt">{{ item.prompt }}</span>
          </div>
          <svg class="aempty__starter-arrow" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="5" y1="12" x2="19" y2="12" />
            <polyline points="12 5 19 12 12 19" />
          </svg>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.aempty {
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

/* Mesh */
.aempty__mesh {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}

.aempty__blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  opacity: 0.4;
}

.aempty__blob--a {
  top: -80px;
  left: 10%;
  width: 360px;
  height: 360px;
  background: radial-gradient(circle, rgba(74, 144, 217, 0.4) 0%, rgba(74, 144, 217, 0) 70%);
}

.aempty__blob--b {
  bottom: -100px;
  right: 10%;
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, rgba(92, 201, 193, 0.35) 0%, rgba(92, 201, 193, 0) 70%);
}

.aempty__grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(74, 144, 217, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(74, 144, 217, 0.04) 1px, transparent 1px);
  background-size: 32px 32px;
  mask-image: radial-gradient(ellipse 70% 60% at 50% 50%, #000 30%, transparent 80%);
  -webkit-mask-image: radial-gradient(ellipse 70% 60% at 50% 50%, #000 30%, transparent 80%);
}

/* Inner */
.aempty__inner {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 740px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  animation: aempty-in 0.6s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes aempty-in {
  from { opacity: 0; transform: translateY(12px); }
  to   { opacity: 1; transform: translateY(0); }
}

/* Mark */
.aempty__mark {
  position: relative;
  width: 60px;
  height: 60px;
  border-radius: 18px;
  background: linear-gradient(135deg, var(--brand-primary), var(--brand-primary-dark));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
  box-shadow:
    0 10px 28px rgba(74, 144, 217, 0.3),
    0 0 0 8px rgba(74, 144, 217, 0.06);
  transition: all 0.35s ease;
}

.aempty__mark.is-kb {
  background: linear-gradient(135deg, var(--brand-accent), var(--brand-accent-dark));
  box-shadow:
    0 10px 28px rgba(92, 201, 193, 0.3),
    0 0 0 8px rgba(92, 201, 193, 0.08);
}

.aempty__mark svg {
  width: 28px;
  height: 28px;
}

.aempty__mark-orbit {
  position: absolute;
  border-radius: 50%;
  border: 1px solid rgba(74, 144, 217, 0.18);
  pointer-events: none;
  animation: aempty-orbit 6s linear infinite;
}

.aempty__mark.is-kb .aempty__mark-orbit {
  border-color: rgba(92, 201, 193, 0.25);
}

.aempty__mark-orbit--1 {
  inset: -14px;
  animation-duration: 10s;
}

.aempty__mark-orbit--2 {
  inset: -28px;
  animation-duration: 16s;
  animation-direction: reverse;
  border-style: dashed;
}

@keyframes aempty-orbit {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}

.aempty__eyebrow {
  margin: 0 0 10px;
  font-family: 'Poppins', sans-serif;
  font-size: 0.72rem;
  font-weight: 600;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--brand-primary);
}

.aempty__title {
  margin: 0 0 14px;
  font-family: 'Poppins', 'Noto Sans SC', sans-serif;
  font-size: 2.4rem;
  font-weight: 800;
  line-height: 1.1;
  color: var(--text-primary);
  letter-spacing: -0.03em;
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-items: center;
}

.aempty__title-line {
  display: inline-block;
}

.aempty__title-line--accent {
  background: linear-gradient(135deg, var(--brand-primary), var(--brand-primary-dark));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  transition: background 0.35s ease;
}

.aempty__title-line--accent.is-kb {
  background: linear-gradient(135deg, var(--brand-accent), var(--brand-accent-dark));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.aempty__subtitle {
  margin: 0 0 22px;
  font-size: 0.95rem;
  line-height: 1.65;
  color: var(--text-secondary);
  max-width: 560px;
}

.aempty__subtitle-warn {
  display: block;
  margin-top: 4px;
  color: #b45309;
  font-size: 0.85rem;
}

/* Starters */
.aempty__starters {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  width: 100%;
  max-width: 680px;
}

.aempty__starter {
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

.aempty__starter:hover:not(:disabled) {
  transform: translateY(-2px);
  border-color: var(--brand-primary);
  box-shadow: 0 10px 24px rgba(74, 144, 217, 0.1);
}

.aempty__starter.is-kb:hover:not(:disabled) {
  border-color: var(--brand-accent-dark);
  box-shadow: 0 10px 24px rgba(92, 201, 193, 0.12);
}

.aempty__starter:hover:not(:disabled) .aempty__starter-arrow {
  color: var(--brand-primary);
  transform: translateX(2px);
}

.aempty__starter.is-kb:hover:not(:disabled) .aempty__starter-arrow {
  color: var(--brand-accent-dark);
}

.aempty__starter:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.aempty__starter-icon {
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

.aempty__starter.is-kb .aempty__starter-icon {
  background: rgba(92, 201, 193, 0.1);
  color: var(--brand-accent-dark);
}

.aempty__starter-icon svg {
  width: 17px;
  height: 17px;
}

.aempty__starter-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.aempty__starter-title {
  font-family: 'Poppins', 'Noto Sans SC', sans-serif;
  font-size: 0.86rem;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.005em;
}

.aempty__starter-prompt {
  font-size: 0.76rem;
  color: var(--text-secondary);
  line-height: 1.45;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.aempty__starter-arrow {
  flex-shrink: 0;
  color: var(--text-muted);
  margin-top: 4px;
  transition: all 0.2s ease;
}

@media (max-width: 720px) {
  .aempty__title {
    font-size: 1.8rem;
  }

  .aempty__starters {
    grid-template-columns: 1fr;
  }
}
</style>


