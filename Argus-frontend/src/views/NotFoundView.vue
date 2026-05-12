<script setup lang="ts">
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<template>
  <div class="not-found">
    <div class="nf-content">
      <span class="nf-code">404</span>
      <h1>页面未找到</h1>
      <p>您访问的页面不存在或已被移除</p>
      <button class="nf-btn" @click="router.push('/')">返回首页</button>
    </div>
  </div>
</template>

<style scoped>
.not-found {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--surface-subtle);
}

.nf-content {
  text-align: center;
  color: var(--text-primary);
}

.nf-code {
  display: block;
  font-family: 'Poppins', sans-serif;
  font-size: 96px;
  font-weight: 800;
  background: linear-gradient(135deg, var(--brand-primary), var(--brand-accent));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  line-height: 1;
  margin-bottom: 16px;
}

.nf-content h1 {
  font-size: 24px;
  font-weight: 700;
  margin-bottom: 8px;
}

.nf-content p {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 32px;
}

.nf-btn {
  padding: 12px 32px;
  border-radius: var(--radius-xs);
  background: var(--brand-primary);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  transition: all 0.2s ease;
}

.nf-btn:hover {
  background: var(--brand-primary-light);
}
</style>
