<script setup lang="ts">
import { computed } from 'vue'
import type { AssistantToolMode } from '@/types/assistant'

const props = defineProps<{
  mode: AssistantToolMode
  disabled?: boolean
  compact?: boolean
}>()

const emit = defineEmits<{
  'update:mode': [mode: AssistantToolMode]
}>()

const isChat = computed(() => props.mode === 'CHAT')

function set(m: AssistantToolMode) {
  if (props.disabled) return
  if (m === props.mode) return
  emit('update:mode', m)
}
</script>

<template>
  <div class="mode-switcher" :class="{ 'is-compact': compact, 'is-disabled': disabled }">
    <span class="mode-switcher__slider" :class="{ 'to-right': !isChat }" />

    <button
      type="button"
      class="mode-switcher__btn"
      :class="{ 'is-active': isChat }"
      @click="set('CHAT')"
    >
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
      </svg>
      <span class="mode-switcher__label">对话</span>
    </button>

    <button
      type="button"
      class="mode-switcher__btn"
      :class="{ 'is-active': !isChat }"
      @click="set('KB_SEARCH')"
    >
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <circle cx="11" cy="11" r="7" />
        <path d="M19 19L16 16" />
        <path d="M7 11h8" />
      </svg>
      <span class="mode-switcher__label">知识库检索</span>
    </button>
  </div>
</template>

<style scoped>
.mode-switcher {
  position: relative;
  display: inline-flex;
  align-items: center;
  padding: 3px;
  background: var(--surface-subtle);
  border: 1px solid var(--border-default);
  border-radius: 100px;
  user-select: none;
}

.mode-switcher.is-disabled {
  opacity: 0.6;
  pointer-events: none;
}

.mode-switcher__slider {
  position: absolute;
  top: 3px;
  bottom: 3px;
  left: 3px;
  width: calc(50% - 3px);
  border-radius: 100px;
  background: linear-gradient(135deg, var(--brand-primary), var(--brand-primary-dark));
  box-shadow:
    0 3px 10px rgba(74, 144, 217, 0.25),
    inset 0 1px 0 rgba(255, 255, 255, 0.15);
  transition: transform 0.3s cubic-bezier(0.16, 1, 0.3, 1), background 0.3s ease;
}

.mode-switcher__slider.to-right {
  transform: translateX(100%);
  background: linear-gradient(135deg, var(--brand-accent), var(--brand-accent-dark));
  box-shadow:
    0 3px 10px rgba(92, 201, 193, 0.3),
    inset 0 1px 0 rgba(255, 255, 255, 0.15);
}

.mode-switcher__btn {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 16px;
  font-family: inherit;
  font-size: 0.8rem;
  font-weight: 600;
  letter-spacing: 0.01em;
  color: var(--text-muted);
  background: transparent;
  border: none;
  border-radius: 100px;
  cursor: pointer;
  transition: color 0.25s ease;
  min-width: 96px;
  justify-content: center;
}

.mode-switcher__btn svg {
  width: 13px;
  height: 13px;
}

.mode-switcher__btn:hover:not(.is-active) {
  color: var(--text-primary);
}

.mode-switcher__btn.is-active {
  color: #fff;
  font-weight: 700;
}

.mode-switcher.is-compact .mode-switcher__btn {
  padding: 5px 12px;
  min-width: 0;
  font-size: 0.74rem;
}

.mode-switcher.is-compact .mode-switcher__btn svg {
  width: 12px;
  height: 12px;
}

.mode-switcher__label {
  white-space: nowrap;
}
</style>
