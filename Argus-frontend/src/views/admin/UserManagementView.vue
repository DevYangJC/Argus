<script setup lang="ts">
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1>用户管理</h1>
      <p>管理系统用户账号、角色与状态（仅系统管理员可访问）</p>
    </div>

    <div class="placeholder">
      <div class="placeholder-icon">
        <svg width="48" height="48" viewBox="0 0 24 24" fill="none">
          <rect x="3" y="3" width="18" height="18" rx="4" stroke="currentColor" stroke-width="1.5"/>
          <path d="M12 8V12L15 14" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        </svg>
      </div>
      <h3>用户管理功能即将上线</h3>
      <p>用户列表 · 角色管理 · 状态管控 · 权限配置</p>
      <p class="api-hint">接口: GET /api/admin/users · PATCH /api/admin/users/{id}/status</p>
    </div>
  </div>
</template>

<style scoped>
.page-header { margin-bottom: 32px; }
.page-header h1 { font-size: 24px; font-weight: 800; color: var(--text-primary); margin-bottom: 4px; }
.page-header p { font-size: 14px; color: var(--text-secondary); margin: 0; }

.placeholder {
  text-align: center; padding: 80px 24px;
  background: var(--surface-white);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
}
.placeholder-icon {
  width: 80px; height: 80px; border-radius: 50%;
  background: rgba(74, 144, 217, 0.08);
  display: flex; align-items: center; justify-content: center;
  color: var(--brand-primary); margin: 0 auto 20px;
}
.placeholder h3 { font-size: 17px; font-weight: 700; color: var(--text-primary); margin-bottom: 8px; }
.placeholder p { font-size: 14px; color: var(--text-secondary); margin: 0; }
.api-hint {
  margin-top: 12px !important; font-size: 12px !important;
  font-family: 'JetBrains Mono', monospace; color: var(--text-muted) !important;
  background: var(--surface-subtle); display: inline-block; padding: 3px 10px; border-radius: 4px;
}
</style>
