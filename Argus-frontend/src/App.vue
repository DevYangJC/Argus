<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
</script>

<template>
  <div v-if="authStore.isBootstrapping" class="bootstrap-overlay">
    <div class="bootstrap-spinner">
      <svg width="48" height="48" viewBox="0 0 32 32" fill="none" class="spinner-svg">
        <defs>
          <linearGradient id="bootstrapGrad" x1="2" y1="2" x2="30" y2="30">
            <stop stop-color="#4A90D9" />
            <stop offset="1" stop-color="#5CC9C1" />
          </linearGradient>
        </defs>
        <circle cx="16" cy="16" r="12" stroke="var(--surface-muted)" stroke-width="2.5" fill="none" />
        <circle
          cx="16" cy="16" r="12" stroke="url(#bootstrapGrad)" stroke-width="2.5"
          fill="none" stroke-dasharray="56 20" stroke-linecap="round"
        />
      </svg>
      <p class="bootstrap-text">正在加载...</p>
    </div>
  </div>
  <router-view v-else />
</template>

<style>
.bootstrap-overlay {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--surface-subtle);
  z-index: 1000;
}

.bootstrap-spinner {
  text-align: center;
}

.spinner-svg {
  animation: spin 1.2s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.bootstrap-text {
  margin-top: 18px;
  font-size: 14px;
  color: var(--text-muted);
  font-weight: 500;
}
</style>
