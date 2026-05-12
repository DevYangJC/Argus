<script setup lang="ts">
defineProps<{
  title: string
  description?: string
  code?: string
  time?: string
  tagText?: string
  tagType?: 'default' | 'accent' | 'success' | 'warning' | 'danger'
}>()

const emit = defineEmits<{
  click: []
}>()
</script>

<template>
  <article 
    class="glass-card" 
    :class="tagType ? `glass-card-${tagType}` : ''"
    @click="emit('click')"
  >
    <div class="glass-card-glow"></div>
    
    <div class="glass-header">
      <h3 class="glass-title">{{ title }}</h3>
      <div v-if="tagText" class="glass-tag">{{ tagText }}</div>
    </div>
    
    <p v-if="description" class="glass-desc">{{ description }}</p>
    <p v-else class="glass-desc text-muted">暂无描述</p>
    
    <slot name="meta"></slot>
    
    <div class="glass-footer" v-if="code || time || $slots.actions">
      <div class="glass-footer-info">
        <span v-if="code" class="glass-code">组织 ID：{{ code }}</span>
        <span v-if="time" class="glass-time">{{ time }}</span>
      </div>
      <div class="glass-actions" v-if="$slots.actions">
        <slot name="actions"></slot>
      </div>
    </div>
  </article>
</template>

<style scoped>
.glass-card {
  position: relative;
  overflow: hidden;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.85); /* Light theme adaptation */
  border: 1px solid var(--border-default);
  border-radius: 20px;
  padding: 36px 28px;
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  flex-direction: column;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03);
}

.glass-card:hover {
  background: rgba(255, 255, 255, 0.95);
  border-color: rgba(139, 92, 246, 0.4);
  transform: translateY(-6px);
  box-shadow: 0 20px 60px rgba(139, 92, 246, 0.15);
}

.glass-card-glow {
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle, rgba(139, 92, 246, 0.08) 0%, transparent 60%);
  opacity: 0;
  transition: opacity 0.4s;
  pointer-events: none;
}
.glass-card:hover .glass-card-glow {
  opacity: 1;
}

/* Accent (Blue) */
.glass-card-accent:hover {
  border-color: rgba(59, 130, 246, 0.4);
  box-shadow: 0 20px 60px rgba(59, 130, 246, 0.15);
}
.glass-card-accent .glass-card-glow {
  background: radial-gradient(circle, rgba(59, 130, 246, 0.08) 0%, transparent 60%);
}
.glass-card-accent .glass-tag {
  background: rgba(59, 130, 246, 0.1);
  border-color: rgba(59, 130, 246, 0.2);
  color: #3b82f6;
}

/* Success (Green) */
.glass-card-success:hover {
  border-color: rgba(16, 185, 129, 0.4);
  box-shadow: 0 20px 60px rgba(16, 185, 129, 0.15);
}
.glass-card-success .glass-card-glow {
  background: radial-gradient(circle, rgba(16, 185, 129, 0.08) 0%, transparent 60%);
}
.glass-card-success .glass-tag {
  background: rgba(16, 185, 129, 0.1);
  border-color: rgba(16, 185, 129, 0.2);
  color: #10b981;
}

/* Warning (Amber) */
.glass-card-warning:hover {
  border-color: rgba(245, 158, 11, 0.4);
  box-shadow: 0 20px 60px rgba(245, 158, 11, 0.15);
}
.glass-card-warning .glass-card-glow {
  background: radial-gradient(circle, rgba(245, 158, 11, 0.08) 0%, transparent 60%);
}
.glass-card-warning .glass-tag {
  background: rgba(245, 158, 11, 0.1);
  border-color: rgba(245, 158, 11, 0.2);
  color: #d97706;
}

/* Danger (Red) */
.glass-card-danger:hover {
  border-color: rgba(239, 68, 68, 0.4);
  box-shadow: 0 20px 60px rgba(239, 68, 68, 0.15);
}
.glass-card-danger .glass-card-glow {
  background: radial-gradient(circle, rgba(239, 68, 68, 0.08) 0%, transparent 60%);
}
.glass-card-danger .glass-tag {
  background: rgba(239, 68, 68, 0.1);
  border-color: rgba(239, 68, 68, 0.2);
  color: #ef4444;
}

.glass-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.glass-title {
  font-size: 1.25rem;
  font-weight: 700;
  margin: 0;
  color: var(--text-primary);
  line-height: 1.4;
  word-break: break-word;
}

.glass-tag {
  flex-shrink: 0;
  display: inline-block;
  padding: 4px 14px;
  background: rgba(139, 92, 246, 0.1);
  border: 1px solid rgba(139, 92, 246, 0.2);
  border-radius: 20px;
  font-size: 0.75rem;
  font-weight: 600;
  color: #8b5cf6;
}

.glass-desc {
  font-size: 0.9rem;
  color: var(--text-secondary);
  line-height: 1.7;
  margin: 0 0 16px 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  flex: 1;
}

.text-muted {
  color: var(--text-muted);
}

.glass-footer {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--border-default);
}

.glass-footer-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.glass-code, .glass-time {
  font-size: 0.8rem;
  color: var(--text-muted);
}

.glass-actions {
  display: flex;
  gap: 8px;
}
</style>
