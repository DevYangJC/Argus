<script setup lang="ts">
defineProps<{
  eyebrow: string
  title: string
  description: string
}>()
</script>

<template>
  <header class="page-header-hero">
    <div class="page-header-hero__copy">
      <p class="page-header-hero__eyebrow">{{ eyebrow }}</p>
      <h1 class="page-header-hero__title">{{ title }}</h1>
      <p class="page-header-hero__desc">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="page-header-hero__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.page-header-hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 24px;
  padding: 24px 28px;
  background: linear-gradient(135deg, rgba(74, 144, 217, 0.03) 0%, rgba(92, 201, 193, 0.03) 100%);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
}

.page-header-hero__eyebrow {
  margin: 0 0 4px;
  font-size: 0.72rem;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--brand-primary);
}

.page-header-hero__title {
  margin: 0;
  font-family: 'Poppins', 'Noto Sans SC', sans-serif;
  font-size: 1.35rem;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.2;
}

.page-header-hero__desc {
  margin: 6px 0 0;
  font-size: 0.88rem;
  color: var(--text-secondary);
  line-height: 1.6;
  max-width: 44rem;
}

.page-header-hero__actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

@media (max-width: 768px) {
  .page-header-hero {
    flex-direction: column;
    padding: 20px;
  }

  .page-header-hero__actions {
    width: 100%;
  }
}
</style>
