<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <div class="empty-state">
    <div class="empty-state__icon">
      <slot name="icon">
        <svg width="48" height="48" viewBox="0 0 24 24" fill="none">
          <circle cx="8" cy="7" r="3" stroke="currentColor" stroke-width="1.2" opacity="0.3"/>
          <circle cx="16" cy="7" r="3" stroke="currentColor" stroke-width="1.2" opacity="0.3"/>
          <path d="M2 20V19C2 16.7909 3.79086 15 6 15H10C12.2091 15 14 16.7909 14 19V20" stroke="currentColor" stroke-width="1.2" opacity="0.3"/>
        </svg>
      </slot>
    </div>
    <h3>{{ title }}</h3>
    <p v-if="description">{{ description }}</p>
    <slot></slot>
  </div>
</template>

<style scoped>
.empty-state {
  text-align: center;
  padding: 64px 24px;
}

.empty-state__icon {
  display: flex;
  justify-content: center;
  margin-bottom: 16px;
  color: var(--text-muted);
}

.empty-state h3 {
  margin: 0 0 8px;
  font-size: 1.05rem;
  color: var(--text-primary);
}

.empty-state p {
  margin: 0 auto;
  max-width: 360px;
  font-size: 0.88rem;
  color: var(--text-secondary);
  line-height: 1.6;
}
</style>
