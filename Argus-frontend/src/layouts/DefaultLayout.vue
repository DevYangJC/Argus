<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const userName = computed(() => authStore.currentUser?.displayName ?? '用户')

// ── 侧边栏折叠 ──
const sidebarCollapsed = ref(false)
function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

// ── 用户菜单 ──
const showUserMenu = ref(false)

function toggleUserMenu() {
  showUserMenu.value = !showUserMenu.value
}

function closeUserMenu() {
  showUserMenu.value = false
}

async function handleLogout() {
  showUserMenu.value = false
  await authStore.logout()
  router.push('/')
}

function onDocumentClick(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (!target.closest('.user-menu-wrapper')) {
    showUserMenu.value = false
  }
}

onMounted(() => document.addEventListener('click', onDocumentClick))
onUnmounted(() => document.removeEventListener('click', onDocumentClick))

// ── 当前激活菜单 ──
const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/app/documents')) return 'documents'
  if (path.startsWith('/app/qa')) return 'qa'
  if (path.startsWith('/app/assistant')) return 'assistant'
  if (path.startsWith('/app/groups')) return 'groups'
  if (path.startsWith('/app/admin/metrics')) return 'metrics'
  if (path.startsWith('/app/admin')) return 'admin'
  if (path.startsWith('/app/settings')) return 'settings'
  return 'groups'
})

// ── 所有菜单项定义（角色无关） ──
const allMenuItems = [
  {
    key: 'documents',
    label: '文档管理',
    icon: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none"><path d="M14 2H7C5.89543 2 5 2.89543 5 4V20C5 21.1046 5.89543 22 7 22H17C18.1046 22 19 21.1046 19 20V7L14 2Z" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/><path d="M14 2V7H19" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/></svg>`,
    path: '/app/documents',
    roles: ['USER'] as const,
  },
  {
    key: 'qa',
    label: '知识库问答',
    icon: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none"><circle cx="11" cy="11" r="7" stroke="currentColor" stroke-width="1.5"/><path d="M19 19L16 16" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>`,
    path: '/app/qa',
    roles: ['USER'] as const,
  },
  {
    key: 'assistant',
    label: 'AI 助手',
    icon: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.5"/><path d="M8.5 12C8.5 10.067 10.067 8.5 12 8.5C13.933 8.5 15.5 10.067 15.5 12C15.5 13.933 13.933 15.5 12 15.5C10.067 15.5 8.5 13.933 8.5 12Z" stroke="currentColor" stroke-width="1.5"/><path d="M18.5 8.5L20 7M5.5 8.5L4 7M18.5 15.5L20 17M5.5 15.5L4 17" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>`,
    path: '/app/assistant',
    roles: ['USER'] as const,
  },
  {
    key: 'groups',
    label: '协作小组',
    icon: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none"><circle cx="8" cy="7" r="3" stroke="currentColor" stroke-width="1.5"/><circle cx="16" cy="7" r="3" stroke="currentColor" stroke-width="1.5"/><path d="M2 20V19C2 16.7909 3.79086 15 6 15H10C12.2091 15 14 16.7909 14 19V20" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/><path d="M14 15H16C18.2091 15 20 16.7909 20 19V20" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>`,
    path: '/app/groups',
    roles: ['USER'] as const,
  },
]

// ── 按角色过滤后的菜单项 ──
const menuItems = computed(() =>
  allMenuItems.filter((item) => {
    const role = authStore.currentUser?.systemRole
    return role && (item.roles as readonly string[]).includes(role)
  }),
)

const allBottomItems = [
  {
    key: 'admin',
    label: '用户管理',
    icon: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none"><rect x="3" y="3" width="18" height="18" rx="4" stroke="currentColor" stroke-width="1.5"/><path d="M12 8V12L15 14" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>`,
    path: '/app/admin/users',
    roles: ['ADMIN'] as const,
  },
  {
    key: 'metrics',
    label: '使用统计',
    icon: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none"><path d="M3 3V16C3 17.1046 3.89543 18 5 18H19C20.1046 18 21 17.1046 21 16V8C21 6.89543 20.1046 6 19 6H12L10 3H5C3.89543 3 3 3.89543 3 5V3Z" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/><path d="M7 14L10 10L13 12L17 8" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>`,
    path: '/app/admin/metrics',
    roles: ['ADMIN'] as const,
  },
  {
    key: 'settings',
    label: '系统设置',
    icon: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="1.5"/><path d="M12 2V4M12 20V22M4.22 4.22L5.64 5.64M18.36 18.36L19.78 19.78M2 12H4M20 12H22M4.22 19.78L5.64 18.36M18.36 5.64L19.78 4.22" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>`,
    path: '/app/settings',
    roles: ['ADMIN', 'USER'] as const,
  },
]

const bottomItems = computed(() =>
  allBottomItems.filter((item) => {
    const role = authStore.currentUser?.systemRole
    return role && (item.roles as readonly string[]).includes(role)
  }),
)

function navigateTo(path: string) {
  router.push(path)
}
</script>

<template>
  <div class="app-layout" :class="{ collapsed: sidebarCollapsed }">
    <!-- ── 侧边栏 ── -->
    <aside class="sidebar">
      <!-- Logo -->
      <div class="sidebar-brand" @click="router.push('/')">
        <div class="sidebar-brand__mark">
          <svg width="22" height="22" viewBox="0 0 32 32" fill="none">
            <defs>
              <linearGradient id="sidebarLogoGrad" x1="2" y1="2" x2="30" y2="30"><stop stop-color="#4A90D9"/><stop offset="1" stop-color="#5CC9C1"/></linearGradient>
              <path id="sPetal" d="M16 3C17.5 6 20 9 22.5 10C20 11 17.5 11.5 16 13C14.5 11.5 12 11 9.5 10C12 9 14.5 6 16 3Z" fill="url(#sidebarLogoGrad)" opacity="0.7"/>
            </defs>
            <use href="#sPetal"/>
            <use href="#sPetal" transform="rotate(90 16 16)"/>
            <use href="#sPetal" transform="rotate(180 16 16)"/>
            <use href="#sPetal" transform="rotate(270 16 16)"/>
            <circle cx="16" cy="16" r="5.5" fill="url(#sidebarLogoGrad)"/>
          </svg>
        </div>
        <div v-show="!sidebarCollapsed" class="sidebar-brand__text">
          <span class="sidebar-brand__eyebrow">Argus · Platform</span>
          <span class="sidebar-brand__title">数据洞察中心</span>
        </div>
      </div>

      <!-- 主导航 -->
      <nav class="sidebar-nav">
        <span v-show="!sidebarCollapsed" class="sidebar-nav__label">Navigation</span>
        <div
          v-for="item in menuItems"
          :key="item.key"
          class="nav-item"
          :class="{ active: activeMenu === item.key }"
          :title="sidebarCollapsed ? item.label : undefined"
          @click="navigateTo(item.path)"
        >
          <span class="nav-item__bar" />
          <span class="nav-icon" v-html="item.icon" />
          <span v-show="!sidebarCollapsed" class="nav-label">{{ item.label }}</span>
          <span v-show="!sidebarCollapsed && activeMenu === item.key" class="nav-item__dot" />
        </div>
      </nav>

      <!-- 底部导航 -->
      <div class="sidebar-bottom">
        <span v-show="!sidebarCollapsed" class="sidebar-nav__label">System</span>
        <div
          v-for="item in bottomItems"
          :key="item.key"
          class="nav-item"
          :class="{ active: activeMenu === item.key }"
          :title="sidebarCollapsed ? item.label : undefined"
          @click="navigateTo(item.path)"
        >
          <span class="nav-item__bar" />
          <span class="nav-icon" v-html="item.icon" />
          <span v-show="!sidebarCollapsed" class="nav-label">{{ item.label }}</span>
          <span v-show="!sidebarCollapsed && activeMenu === item.key" class="nav-item__dot" />
        </div>
      </div>
    </aside>

    <!-- ── 主区域 ── -->
    <div class="main-area">
      <!-- 顶栏 -->
      <header class="topbar">
        <div class="topbar-left">
          <button class="btn-sidebar-toggle" :title="sidebarCollapsed ? '展开侧边栏' : '折叠侧边栏'" @click="toggleSidebar">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
              <path d="M4 6H20M4 12H14M4 18H20" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            </svg>
          </button>
          <div class="breadcrumb">
            <span class="breadcrumb-eyebrow">Argus</span>
            <span class="breadcrumb-sep">/</span>
            <span class="breadcrumb-current">{{ route.meta.title || '数据洞察中心' }}</span>
          </div>
        </div>
        <div class="topbar-right">
          <div class="user-menu-wrapper">
            <button class="user-chip" :class="{ 'is-open': showUserMenu }" @click="toggleUserMenu">
              <span class="user-chip__avatar">{{ userName.charAt(0).toUpperCase() }}</span>
              <span class="user-chip__text">
                <span class="user-chip__name">{{ userName }}</span>
                <span class="user-chip__role">{{ authStore.isAdmin ? '系统管理员' : '用户' }}</span>
              </span>
              <svg class="user-chip__caret" width="12" height="12" viewBox="0 0 24 24" fill="none">
                <path d="M6 9l6 6 6-6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </button>
            <!-- 下拉菜单 -->
            <div v-if="showUserMenu" class="user-dropdown">
              <div class="dropdown-header">
                <div class="dropdown-header__avatar">{{ userName.charAt(0).toUpperCase() }}</div>
                <div class="dropdown-header__text">
                  <span class="dropdown-name">{{ userName }}</span>
                  <span class="dropdown-role">{{ authStore.isAdmin ? '系统管理员' : '用户' }}</span>
                </div>
              </div>
              <div class="dropdown-divider"></div>
              <button class="dropdown-item" @click="handleLogout">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none">
                  <path d="M9 21H5C4.46957 21 3.96086 20.7893 3.58579 20.4142C3.21071 20.0391 3 19.5304 3 19V5C3 4.46957 3.21071 3.96086 3.58579 3.58579C3.96086 3.21071 4.46957 3 5 3H9" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" />
                  <path d="M16 17L21 12L16 7" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" />
                  <path d="M21 12H9" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
                </svg>
                <span>退出登录</span>
              </button>
            </div>
          </div>
        </div>
      </header>

      <!-- 内容区 -->
      <main class="main-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
/* ════════════ Layout ════════════ */
.app-layout {
  display: flex;
  min-height: 100vh;
  background: var(--surface-subtle);
}

/* ════════════ Sidebar ════════════ */
.sidebar {
  width: 244px;
  flex-shrink: 0;
  background: #fbfaf6;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--border-default);
  transition: width 0.25s cubic-bezier(0.16, 1, 0.3, 1);
  overflow: hidden;
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  z-index: 50;
}

.sidebar::before {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 1px;
  background: linear-gradient(to bottom, transparent, rgba(74, 144, 217, 0.15), transparent);
  pointer-events: none;
}

.collapsed .sidebar {
  width: 68px;
}

/* Brand */
.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 20px 16px;
  cursor: pointer;
  border-bottom: 1px dashed var(--border-default);
  flex-shrink: 0;
  position: relative;
}

.sidebar-brand__mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(74, 144, 217, 0.1);
  flex-shrink: 0;
  transition: transform 0.25s ease;
}

.sidebar-brand:hover .sidebar-brand__mark {
  transform: rotate(6deg) scale(1.05);
}

.sidebar-brand__text {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.sidebar-brand__eyebrow {
  font-family: 'Poppins', sans-serif;
  font-size: 0.6rem;
  font-weight: 600;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.sidebar-brand__title {
  font-family: 'Poppins', 'Noto Sans SC', sans-serif;
  font-weight: 700;
  font-size: 0.95rem;
  color: var(--text-primary);
  white-space: nowrap;
  letter-spacing: -0.01em;
}

.sidebar-nav {
  flex: 1;
  padding: 14px 10px 8px;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: var(--border-default) transparent;
}

.sidebar-nav::-webkit-scrollbar {
  width: 4px;
}

.sidebar-nav::-webkit-scrollbar-thumb {
  background: var(--border-default);
  border-radius: 2px;
}

.sidebar-nav__label {
  display: block;
  padding: 4px 12px 8px;
  font-family: 'Poppins', sans-serif;
  font-size: 0.66rem;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.sidebar-bottom {
  padding: 10px 10px 16px;
  border-top: 1px dashed var(--border-default);
}

.brand-text {
  display: none;
}

/* Collapsed tweaks */
.collapsed .sidebar-brand {
  justify-content: center;
  padding: 20px 0;
}

.collapsed .sidebar-nav {
  padding: 14px 8px 8px;
}

.collapsed .sidebar-bottom {
  padding: 10px 8px 16px;
}

.collapsed .sidebar-nav__label {
  display: none;
}

/* ════════════ Nav items ════════════ */
.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 9px 11px 9px 13px;
  margin-bottom: 2px;
  border-radius: 9px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.15s ease;
  font-size: 0.88rem;
  font-weight: 500;
  white-space: nowrap;
}

.nav-item:hover {
  background: rgba(74, 144, 217, 0.06);
  color: var(--text-primary);
}

.nav-item.active {
  background: #fff;
  color: var(--brand-primary);
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.nav-item__bar {
  position: absolute;
  left: 4px;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 20px;
  border-radius: 2px;
  background: transparent;
  transition: background 0.2s ease;
}

.nav-item.active .nav-item__bar {
  background: linear-gradient(to bottom, var(--brand-primary), var(--brand-accent));
}

.nav-icon {
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.nav-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
}

.nav-item__dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--brand-accent);
  flex-shrink: 0;
  box-shadow: 0 0 6px rgba(92, 201, 193, 0.5);
}

.collapsed .nav-item {
  padding: 9px;
  justify-content: center;
}

.collapsed .nav-item__bar {
  left: 0;
}

/* ════════════ Main Area ════════════ */
.main-area {
  flex: 1;
  margin-left: 244px;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  transition: margin-left 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}

.collapsed .main-area {
  margin-left: 68px;
}

/* ════════════ Topbar ════════════ */
.topbar {
  height: 64px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: saturate(180%) blur(12px);
  border-bottom: 1px solid var(--border-default);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28px;
  position: sticky;
  top: 0;
  z-index: 40;
  flex-shrink: 0;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.btn-sidebar-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: transparent;
  border: 1px solid transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-sidebar-toggle:hover {
  background: var(--surface-subtle);
  border-color: var(--border-default);
  color: var(--brand-primary);
}

.breadcrumb {
  display: flex;
  align-items: baseline;
  gap: 7px;
}

.breadcrumb-eyebrow {
  font-family: 'Poppins', sans-serif;
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.breadcrumb-sep {
  color: var(--text-muted);
  font-size: 0.86rem;
  font-weight: 300;
}

.breadcrumb-current {
  font-family: 'Poppins', 'Noto Sans SC', sans-serif;
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.01em;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}


/* User chip */
.user-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 5px 10px 5px 5px;
  background: #fff;
  border: 1px solid var(--border-default);
  border-radius: 100px;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.18s ease;
}

.user-chip:hover {
  border-color: var(--brand-primary);
  box-shadow: 0 2px 8px rgba(74, 144, 217, 0.12);
}

.user-chip.is-open {
  border-color: var(--brand-primary);
  box-shadow: 0 0 0 3px rgba(74, 144, 217, 0.12);
}

.user-chip__avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--brand-primary), var(--brand-accent));
  color: #fff;
  font-size: 0.76rem;
  font-weight: 700;
  flex-shrink: 0;
  box-shadow: 0 2px 6px rgba(74, 144, 217, 0.25);
}

.user-chip__text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
  text-align: left;
}

.user-chip__name {
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--text-primary);
}

.user-chip__role {
  font-family: 'Poppins', sans-serif;
  font-size: 0.62rem;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.user-chip__caret {
  color: var(--text-muted);
  transition: transform 0.2s ease;
  flex-shrink: 0;
}

.user-chip.is-open .user-chip__caret {
  transform: rotate(180deg);
  color: var(--brand-primary);
}

/* Hide extra text when very narrow */
@media (max-width: 640px) {
  .user-chip__text {
    display: none;
  }

  .user-chip {
    padding: 5px;
  }
}


/* ── 用户下拉菜单 ── */
.user-menu-wrapper {
  position: relative;
}

.user-dropdown {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  width: 240px;
  background: #fff;
  border: 1px solid var(--border-default);
  border-radius: 14px;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.1);
  z-index: 100;
  animation: dropdown-in 0.18s cubic-bezier(0.16, 1, 0.3, 1);
  overflow: hidden;
}

@keyframes dropdown-in {
  from {
    opacity: 0;
    transform: translateY(-6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.dropdown-header {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 14px 16px 12px;
  background: linear-gradient(135deg, rgba(74, 144, 217, 0.04), rgba(92, 201, 193, 0.04));
  border-bottom: 1px dashed var(--border-default);
}

.dropdown-header__avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--brand-primary), var(--brand-accent));
  color: #fff;
  font-size: 0.92rem;
  font-weight: 700;
  box-shadow: 0 4px 12px rgba(74, 144, 217, 0.25);
  flex-shrink: 0;
}

.dropdown-header__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.dropdown-name {
  display: block;
  font-size: 0.9rem;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.25;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dropdown-role {
  display: block;
  font-family: 'Poppins', sans-serif;
  font-size: 0.62rem;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--brand-primary);
}

.dropdown-divider {
  display: none;
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 11px 16px;
  font-family: inherit;
  font-size: 0.85rem;
  font-weight: 500;
  color: var(--text-secondary);
  background: transparent;
  border: none;
  cursor: pointer;
  transition: all 0.15s ease;
}

.dropdown-item:hover {
  background: rgba(239, 68, 68, 0.06);
  color: var(--el-color-danger);
}


/* ════════════ Content ════════════ */
.main-content {
  flex: 1;
  padding: 28px 32px;
}

/* ════════════ Responsive ════════════ */
@media (max-width: 768px) {
  .sidebar {
    width: 0;
  }

  .app-layout:not(.collapsed) .sidebar {
    width: 244px;
  }

  .main-area {
    margin-left: 0;
  }

  .collapsed .sidebar {
    width: 0;
  }

  .main-content {
    padding: 20px 16px;
  }
}
</style>
