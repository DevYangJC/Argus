<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessageBox } from 'element-plus'
import {
  fetchAdminUsers,
  fetchAdminUserDetail,
  updateAdminUserStatus,
  type AdminUserItem,
  type UserStatus,
} from '@/api/admin-user'
import { extractApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import PageHeaderHero from '@/components/layout/PageHeaderHero.vue'

const authStore = useAuthStore()

/** 当前登录用户 ID，用于防止误禁用自身 */
const currentUserId = computed(() => authStore.currentUser?.userId ?? null)

// ── 数据状态 ──
const users = ref<AdminUserItem[]>([])
const isLoading = ref(false)
const errorMsg = ref('')
const feedbackMsg = ref('')

// ── 用户详情侧边栏 ──
const detailUser = ref<AdminUserItem | null>(null)
const isDetailLoading = ref(false)
const showDetailPanel = ref(false)

// ── 状态筛选标签 ──
type FilterTab = 'ALL' | 'ACTIVE' | 'DISABLED'
const activeFilter = ref<FilterTab>('ALL')

const filterTabs: { key: FilterTab; label: string }[] = [
  { key: 'ALL', label: '全部用户' },
  { key: 'ACTIVE', label: '正常' },
  { key: 'DISABLED', label: '已禁用' },
]

// ── 计算属性 ──
const filteredUsers = computed(() => {
  if (activeFilter.value === 'ALL') return users.value
  return users.value.filter((u) => u.status === activeFilter.value)
})

const activeCount = computed(() => users.value.filter((u) => u.status === 'ACTIVE').length)
const disabledCount = computed(() => users.value.filter((u) => u.status === 'DISABLED').length)

// ── 格式化工具 ──
function formatDateTime(iso: string | null): string {
  if (!iso) return '从未登录'
  try {
    const d = new Date(iso)
    return d.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return iso
  }
}

function getRoleLabel(role: string): string {
  return role === 'ADMIN' ? '系统管理员' : '普通用户'
}

function getStatusLabel(status: UserStatus): string {
  return status === 'ACTIVE' ? '正常' : '已禁用'
}

function getInitial(name: string): string {
  return name.charAt(0).toUpperCase()
}

// ── 头像颜色分配 ──
const AVATAR_COLORS = [
  'linear-gradient(135deg, #f59e0b, #ea580c)',
  'linear-gradient(135deg, #6366f1, #4f46e5)',
  'linear-gradient(135deg, #10b981, #059669)',
  'linear-gradient(135deg, #64748b, #475569)',
  'linear-gradient(135deg, #ef4444, #dc2626)',
  'linear-gradient(135deg, #8b5cf6, #7c3aed)',
  'linear-gradient(135deg, #06b6d4, #0891b2)',
  'linear-gradient(135deg, #f97316, #ea580c)',
]

function getAvatarColor(index: number): string {
  return AVATAR_COLORS[index % AVATAR_COLORS.length]!
}

// ── 数据加载 ──
onMounted(() => {
  void loadUsers()
})

async function loadUsers() {
  isLoading.value = true
  errorMsg.value = ''
  try {
    users.value = await fetchAdminUsers()
  } catch (err) {
    errorMsg.value = extractApiError(err, '加载用户列表失败')
    users.value = []
  } finally {
    isLoading.value = false
  }
}

// ═══════════════════════════════════════
// 用户详情侧边栏
// ═══════════════════════════════════════

async function openUserDetail(userId: number) {
  isDetailLoading.value = true
  detailUser.value = null
  showDetailPanel.value = true
  errorMsg.value = ''
  try {
    detailUser.value = await fetchAdminUserDetail(userId)
  } catch (err) {
    errorMsg.value = extractApiError(err, '加载用户详情失败')
  } finally {
    isDetailLoading.value = false
  }
}

function closeDetailPanel() {
  showDetailPanel.value = false
  detailUser.value = null
}

// ═══════════════════════════════════════
// 状态切换 (启用/禁用)
// ═══════════════════════════════════════

async function toggleUserStatus(user: AdminUserItem) {
  const newStatus: UserStatus = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  const actionLabel = newStatus === 'DISABLED' ? '禁用' : '启用'

  try {
    await ElMessageBox.confirm(
      `确定要${actionLabel}用户「${user.displayName}」(${user.username}) 吗？${
        newStatus === 'DISABLED' ? '禁用后该用户将无法登录系统。' : ''
      }`,
      `确认${actionLabel}`,
      {
        confirmButtonText: actionLabel,
        cancelButtonText: '取消',
        type: newStatus === 'DISABLED' ? 'warning' : 'info',
      },
    )
  } catch {
    return // 用户取消
  }

  feedbackMsg.value = ''
  errorMsg.value = ''

  try {
    await updateAdminUserStatus(user.userId, newStatus)
    // 更新本地列表
    const idx = users.value.findIndex((u) => u.userId === user.userId)
    if (idx !== -1) {
      users.value[idx] = { ...users.value[idx]!, status: newStatus }
    }
    feedbackMsg.value = `已${actionLabel}用户「${user.displayName}」。`
    // 3 秒后自动清除反馈
    setTimeout(() => { feedbackMsg.value = '' }, 3000)
  } catch (err) {
    errorMsg.value = extractApiError(err, `${actionLabel}用户失败`)
  }
}

// ═══════════════════════════════════════
// 键盘事件：ESC 关闭详情面板
// ═══════════════════════════════════════

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && showDetailPanel.value) {
    closeDetailPanel()
  }
}

// 组件挂载/卸载时注册全局键盘监听
if (typeof window !== 'undefined') {
  window.addEventListener('keydown', onKeydown)
}
</script>

<template>
  <div class="user-mgmt-page">
    <!-- ── 页面标题 ── -->
    <PageHeaderHero
      eyebrow="系统管理"
      title="用户管理"
      description="管理系统用户账号、角色与状态，仅系统管理员可访问"
    >
      <template #actions>
        <button class="action-btn" @click="loadUsers">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
          刷新
        </button>
      </template>
    </PageHeaderHero>

    <!-- ── 反馈消息 ── -->
    <div v-if="feedbackMsg || errorMsg" class="feedback-area">
      <p v-if="feedbackMsg" class="feedback feedback--success">{{ feedbackMsg }}</p>
      <p v-if="errorMsg" class="feedback feedback--error">{{ errorMsg }}</p>
    </div>

    <!-- ── 加载状态 ── -->
    <div v-if="isLoading" class="loading-state">
      <div class="spinner"></div>
      <p>正在加载用户数据...</p>
    </div>

    <template v-else>
      <!-- ── 状态统计标签 ── -->
      <div class="filter-tabs">
        <button
          v-for="tab in filterTabs"
          :key="tab.key"
          class="filter-tab"
          :class="{ 'is-active': activeFilter === tab.key }"
          type="button"
          @click="activeFilter = tab.key"
        >
          {{ tab.label }}
          <span v-if="tab.key === 'ALL'" class="tab-count">{{ users.length }}</span>
          <span v-else-if="tab.key === 'ACTIVE'" class="tab-count tab-count--active">{{ activeCount }}</span>
          <span v-else-if="tab.key === 'DISABLED'" class="tab-count tab-count--disabled">{{ disabledCount }}</span>
        </button>
      </div>

      <!-- ── 空状态 ── -->
      <div v-if="filteredUsers.length === 0" class="empty-state">
        <div class="empty-icon">
          <svg width="40" height="40" viewBox="0 0 24 24" fill="none">
            <circle cx="11" cy="11" r="7" stroke="currentColor" stroke-width="1.5"/>
            <path d="M19 19L16 16" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
        </div>
        <h3 v-if="activeFilter === 'ALL'">暂无用户数据</h3>
        <h3 v-else-if="activeFilter === 'ACTIVE'">暂无正常状态用户</h3>
        <h3 v-else>暂无已禁用用户</h3>
        <p v-if="activeFilter !== 'ALL'" class="empty-hint">尝试切换筛选条件查看更多用户</p>
      </div>

      <!-- ── 用户列表表格 ── -->
      <div v-else class="user-table-wrapper">
        <!-- 表头 -->
        <div class="user-table-header">
          <div class="th th--user">用户</div>
          <div class="th th--email">邮箱</div>
          <div class="th th--role">角色</div>
          <div class="th th--status">状态</div>
          <div class="th th--login">最后登录</div>
          <div class="th th--actions">操作</div>
        </div>

        <!-- 表格行 -->
        <div
          v-for="(user, idx) in filteredUsers"
          :key="user.userId"
          class="user-row"
        >
          <!-- 用户信息 -->
          <div class="td td--user">
            <div class="user-avatar" :style="{ background: getAvatarColor(idx) }">
              {{ getInitial(user.displayName) }}
            </div>
            <div class="user-info">
              <span class="user-name">{{ user.displayName }}</span>
              <span class="user-meta">
                @{{ user.username }}
                <span class="user-id">#{{ user.userId }}</span>
              </span>
            </div>
          </div>

          <!-- 邮箱 -->
          <div class="td td--email">
            <span class="email-text">{{ user.email }}</span>
          </div>

          <!-- 角色 -->
          <div class="td td--role">
            <span class="role-badge" :class="{ 'role-badge--admin': user.systemRole === 'ADMIN' }">
              {{ getRoleLabel(user.systemRole) }}
            </span>
          </div>

          <!-- 状态 -->
          <div class="td td--status">
            <span class="status-dot" :class="{ 'status-dot--active': user.status === 'ACTIVE' }"></span>
            <span class="status-text" :class="{ 'status-text--disabled': user.status === 'DISABLED' }">
              {{ getStatusLabel(user.status) }}
            </span>
          </div>

          <!-- 最后登录 -->
          <div class="td td--login">
            <span class="login-time">{{ formatDateTime(user.lastLoginAt) }}</span>
          </div>

          <!-- 操作 -->
          <div class="td td--actions">
            <button
              class="row-btn row-btn--detail"
              title="查看详情"
              @click="openUserDetail(user.userId)"
            >
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="1.5" />
                <circle cx="12" cy="5" r="1.5" />
                <circle cx="12" cy="19" r="1.5" />
              </svg>
            </button>
            <button
              class="row-btn"
              :class="{
                'row-btn--disable': user.status === 'ACTIVE',
                'row-btn--enable': user.status === 'DISABLED'
              }"
              :title="user.status === 'ACTIVE' ? '禁用用户' : '启用用户'"
              :disabled="user.userId === currentUserId"
              @click="toggleUserStatus(user)"
            >
              <template v-if="user.userId === currentUserId">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M12 15v-2m0-4h.01M12 2a10 10 0 100 20 10 10 0 000-20z" />
                </svg>
                当前用户
              </template>
              <template v-else>
                <svg v-if="user.status === 'ACTIVE'" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path stroke-linecap="round" d="M18.36 6.64L18.36 6.64A9 9 0 0112 21a9 9 0 01-6.36-15.36" />
                </svg>
                <svg v-else width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
                </svg>
                {{ user.status === 'ACTIVE' ? '禁用' : '启用' }}
              </template>
            </button>
          </div>
        </div>
      </div>
    </template>

    <!-- ═══════════════════════════════════════ -->
    <!--  用户详情侧边面板                        -->
    <!-- ═══════════════════════════════════════ -->
    <Teleport to="body">
      <Transition name="panel-slide">
        <div
          v-if="showDetailPanel"
          class="detail-overlay"
          @click.self="closeDetailPanel"
        >
          <div class="detail-panel">
            <!-- 面板头部 -->
            <div class="detail-panel__header">
              <h3 class="detail-panel__title">用户详情</h3>
              <button class="detail-panel__close" @click="closeDetailPanel">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M18 6L6 18M6 6l12 12" />
                </svg>
              </button>
            </div>

            <!-- 面板内容 -->
            <div class="detail-panel__body">
              <!-- 加载中 -->
              <div v-if="isDetailLoading" class="detail-loading">
                <div class="spinner"></div>
                <p>加载用户详情...</p>
              </div>

              <!-- 详情内容 -->
              <div v-else-if="detailUser" class="detail-content">
                <!-- 用户头像与基本信息 -->
                <div class="detail-profile">
                  <div
                    class="detail-avatar"
                    :style="{ background: getAvatarColor(detailUser.userId % AVATAR_COLORS.length) }"
                  >
                    {{ getInitial(detailUser.displayName) }}
                  </div>
                  <div class="detail-profile__info">
                    <h2 class="detail-name">{{ detailUser.displayName }}</h2>
                    <p class="detail-username">@{{ detailUser.username }} · #{{ detailUser.userId }}</p>
                  </div>
                </div>

                <!-- 详情字段列表 -->
                <dl class="detail-fields">
                  <div class="detail-field">
                    <dt>用户编码</dt>
                    <dd><code>{{ detailUser.userCode }}</code></dd>
                  </div>
                  <div class="detail-field">
                    <dt>邮箱</dt>
                    <dd>{{ detailUser.email }}</dd>
                  </div>
                  <div class="detail-field">
                    <dt>系统角色</dt>
                    <dd>
                      <span class="role-badge" :class="{ 'role-badge--admin': detailUser.systemRole === 'ADMIN' }">
                        {{ getRoleLabel(detailUser.systemRole) }}
                      </span>
                    </dd>
                  </div>
                  <div class="detail-field">
                    <dt>账号状态</dt>
                    <dd>
                      <span class="status-dot" :class="{ 'status-dot--active': detailUser.status === 'ACTIVE' }"></span>
                      <span class="status-text" :class="{ 'status-text--disabled': detailUser.status === 'DISABLED' }">
                        {{ getStatusLabel(detailUser.status) }}
                      </span>
                    </dd>
                  </div>
                  <div class="detail-field">
                    <dt>密码状态</dt>
                    <dd>
                      <span v-if="detailUser.mustChangePassword" class="field-tag field-tag--warn">
                        需修改密码
                      </span>
                      <span v-else class="field-tag field-tag--ok">正常</span>
                    </dd>
                  </div>
                  <div class="detail-field">
                    <dt>最后登录</dt>
                    <dd>{{ formatDateTime(detailUser.lastLoginAt) }}</dd>
                  </div>
                </dl>

                <!-- 操作按钮 -->
                <div v-if="detailUser.userId !== currentUserId" class="detail-actions">
                  <button
                    class="detail-action-btn"
                    :class="{
                      'detail-action-btn--danger': detailUser.status === 'ACTIVE',
                      'detail-action-btn--primary': detailUser.status === 'DISABLED'
                    }"
                    @click="toggleUserStatus(detailUser)"
                  >
                    {{ detailUser.status === 'ACTIVE' ? '禁用该用户' : '启用该用户' }}
                  </button>
                </div>
                <div v-else class="detail-actions">
                  <p class="detail-self-hint">这是您的账号，无法在此处修改自身状态</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
/* ════════════ Page Layout ════════════ */
.user-mgmt-page {
  width: 100%;
  min-height: 100%;
}

/* ════════════ Action Buttons (Header) ════════════ */
.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 8px 16px;
  border-radius: var(--radius-sm);
  font-size: 0.86rem;
  font-weight: 600;
  border: 1px solid var(--border-default);
  background: var(--surface-white);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s ease;
}

.action-btn:hover {
  color: var(--text-primary);
  border-color: var(--text-muted);
  background: var(--surface-subtle);
}

/* ════════════ Feedback ════════════ */
.feedback-area {
  margin-bottom: 8px;
}

.feedback {
  margin: 0 0 8px;
  padding: 10px 14px;
  border-radius: var(--radius-sm);
  font-size: 0.86rem;
  line-height: 1.5;
}

.feedback--success {
  background: rgba(16, 185, 129, 0.08);
  color: #10b981;
  border: 1px solid rgba(16, 185, 129, 0.15);
}

.feedback--error {
  background: rgba(239, 68, 68, 0.08);
  color: #ef4444;
  border: 1px solid rgba(239, 68, 68, 0.15);
}

/* ════════════ Loading ════════════ */
.loading-state {
  text-align: center;
  padding: 80px 24px;
}

.loading-state p {
  margin-top: 16px;
  font-size: 0.9rem;
  color: var(--text-muted);
}

.spinner {
  width: 32px;
  height: 32px;
  margin: 0 auto;
  border: 3px solid var(--surface-muted);
  border-top-color: var(--brand-primary);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ════════════ Filter Tabs ════════════ */
.filter-tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 20px;
}

.filter-tab {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 10px 18px;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--surface-white);
  color: var(--text-secondary);
  font-size: 0.88rem;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid var(--border-subtle);
}

.filter-tab:hover {
  color: var(--text-primary);
  border-color: var(--border-default);
}

.filter-tab.is-active {
  background: var(--brand-primary);
  color: #fff;
  border-color: var(--brand-primary);
}

.tab-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  font-size: 0.72rem;
  font-weight: 700;
  background: var(--surface-muted);
  color: var(--text-secondary);
}

.filter-tab.is-active .tab-count {
  background: rgba(255, 255, 255, 0.25);
  color: #fff;
}

/* ════════════ Empty State ════════════ */
.empty-state {
  text-align: center;
  padding: 64px 24px;
  background: var(--surface-white);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
}

.empty-icon {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: rgba(74, 144, 217, 0.06);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--brand-primary);
  margin: 0 auto 16px;
}

.empty-state h3 {
  font-size: 1rem;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 6px;
}

.empty-state .empty-hint {
  font-size: 0.84rem;
  color: var(--text-muted);
  margin: 0;
}

/* ════════════ User Table ════════════ */
.user-table-wrapper {
  background: var(--surface-white);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  overflow: hidden;
}

/* 表头 */
.user-table-header {
  display: grid;
  grid-template-columns: minmax(180px, 2fr) minmax(140px, 1.5fr) minmax(90px, 0.8fr) minmax(80px, 0.7fr) minmax(120px, 1fr) minmax(100px, 0.8fr);
  gap: 16px;
  align-items: center;
  padding: 14px 20px;
  background: var(--surface-subtle);
  border-bottom: 1px solid var(--border-default);
}

.th {
  font-size: 0.74rem;
  font-weight: 700;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

/* 数据行 */
.user-row {
  display: grid;
  grid-template-columns: minmax(180px, 2fr) minmax(140px, 1.5fr) minmax(90px, 0.8fr) minmax(80px, 0.7fr) minmax(120px, 1fr) minmax(100px, 0.8fr);
  gap: 16px;
  align-items: center;
  padding: 14px 20px;
  border-bottom: 1px solid var(--border-subtle);
  transition: background 0.15s ease;
}

.user-row:last-child {
  border-bottom: none;
}

.user-row:hover {
  background: rgba(74, 144, 217, 0.02);
}

/* 用户信息列 */
.td--user {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.user-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  color: #fff;
  font-size: 0.82rem;
  font-weight: 700;
  flex-shrink: 0;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
}

.user-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 1px;
}

.user-name {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-meta {
  font-size: 0.76rem;
  color: var(--text-muted);
  display: flex;
  align-items: center;
  gap: 6px;
}

.user-id {
  font-family: 'JetBrains Mono', 'Consolas', monospace;
  font-size: 0.7rem;
  color: var(--text-muted);
  opacity: 0.7;
}

/* 邮箱列 */
.email-text {
  font-size: 0.84rem;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}

/* 角色徽章 */
.role-badge {
  display: inline-flex;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 0.74rem;
  font-weight: 600;
  background: var(--surface-subtle);
  color: var(--text-secondary);
  border: 1px solid var(--border-default);
}

.role-badge--admin {
  background: rgba(74, 144, 217, 0.08);
  color: var(--brand-primary);
  border-color: rgba(74, 144, 217, 0.2);
}

/* 状态 */
.status-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #d1d5db;
  margin-right: 6px;
  flex-shrink: 0;
}

.status-dot--active {
  background: #10b981;
}

.status-text {
  font-size: 0.84rem;
  font-weight: 600;
  color: var(--text-primary);
}

.status-text--disabled {
  color: var(--text-muted);
}

/* 登录时间 */
.login-time {
  font-size: 0.82rem;
  color: var(--text-secondary);
}

/* 操作按钮 */
.td--actions {
  display: flex;
  gap: 6px;
  justify-content: flex-end;
}

.row-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-default);
  background: var(--surface-white);
  font-size: 0.78rem;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.18s ease;
  white-space: nowrap;
}

.row-btn--detail {
  color: var(--text-secondary);
  padding: 6px 8px;
}

.row-btn--detail:hover {
  background: var(--surface-subtle);
  color: var(--text-primary);
  border-color: var(--text-muted);
}

.row-btn--disable {
  color: #ef4444;
  border-color: rgba(239, 68, 68, 0.15);
  background: rgba(239, 68, 68, 0.04);
}

.row-btn--disable:hover {
  background: rgba(239, 68, 68, 0.1);
  border-color: rgba(239, 68, 68, 0.3);
}

.row-btn--enable {
  color: #10b981;
  border-color: rgba(16, 185, 129, 0.15);
  background: rgba(16, 185, 129, 0.04);
}

.row-btn--enable:hover {
  background: rgba(16, 185, 129, 0.1);
  border-color: rgba(16, 185, 129, 0.3);
}

.row-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
  background: var(--surface-subtle);
  color: var(--text-muted);
  border-color: var(--border-subtle);
}

/* ════════════ User Detail Side Panel ════════════ */
.detail-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.25);
  backdrop-filter: blur(2px);
  z-index: 100;
  display: flex;
  justify-content: flex-end;
}

.detail-panel {
  width: 420px;
  max-width: 100vw;
  height: 100vh;
  background: var(--surface-white);
  border-left: 1px solid var(--border-default);
  box-shadow: -4px 0 24px rgba(15, 23, 42, 0.08);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.detail-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid var(--border-default);
  flex-shrink: 0;
}

.detail-panel__title {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--text-primary);
}

.detail-panel__close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.15s ease;
}

.detail-panel__close:hover {
  background: var(--surface-subtle);
  border-color: var(--border-default);
  color: var(--text-primary);
}

.detail-panel__body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}

.detail-loading {
  text-align: center;
  padding: 60px 0;
}

.detail-loading p {
  margin-top: 14px;
  font-size: 0.88rem;
  color: var(--text-muted);
}

/* 用户概要 */
.detail-profile {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-bottom: 20px;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--border-subtle);
}

.detail-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 14px;
  color: #fff;
  font-size: 1.3rem;
  font-weight: 700;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
}

.detail-profile__info {
  min-width: 0;
}

.detail-name {
  margin: 0;
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-username {
  margin: 3px 0 0;
  font-size: 0.82rem;
  color: var(--text-muted);
}

/* 详情字段 */
.detail-fields {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-field {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.detail-field dt {
  font-size: 0.7rem;
  font-weight: 700;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.detail-field dd {
  margin: 0;
  font-size: 0.9rem;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  gap: 6px;
}

.detail-field dd code {
  font-family: 'JetBrains Mono', 'Consolas', monospace;
  font-size: 0.82rem;
  background: var(--surface-subtle);
  padding: 2px 8px;
  border-radius: 4px;
  color: var(--text-secondary);
}

/* 字段标签 */
.field-tag {
  display: inline-flex;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 0.74rem;
  font-weight: 600;
}

.field-tag--warn {
  background: rgba(245, 158, 11, 0.1);
  color: #d97706;
  border: 1px solid rgba(245, 158, 11, 0.2);
}

.field-tag--ok {
  background: rgba(16, 185, 129, 0.08);
  color: #10b981;
  border: 1px solid rgba(16, 185, 129, 0.15);
}

/* 面板操作按钮 */
.detail-actions {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--border-subtle);
}

.detail-action-btn {
  width: 100%;
  padding: 10px 18px;
  border-radius: var(--radius-sm);
  font-size: 0.9rem;
  font-weight: 600;
  font-family: inherit;
  border: 1px solid;
  cursor: pointer;
  transition: all 0.2s ease;
}

.detail-action-btn--primary {
  background: var(--brand-primary);
  border-color: var(--brand-primary);
  color: #fff;
}

.detail-action-btn--primary:hover {
  background: var(--brand-primary-dark);
  border-color: var(--brand-primary-dark);
}

.detail-action-btn--danger {
  background: transparent;
  border-color: rgba(239, 68, 68, 0.25);
  color: #ef4444;
}

.detail-action-btn--danger:hover {
  background: rgba(239, 68, 68, 0.08);
  border-color: rgba(239, 68, 68, 0.4);
}

.detail-self-hint {
  margin: 0;
  font-size: 0.84rem;
  color: var(--text-muted);
  text-align: center;
}

/* ════════════ Side Panel Enter/Leave Transition ════════════ */
.panel-slide-enter-active,
.panel-slide-leave-active {
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}

.panel-slide-enter-active .detail-panel,
.panel-slide-leave-active .detail-panel {
  transition: transform 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}

.panel-slide-enter-from,
.panel-slide-leave-to {
  background: transparent;
}

.panel-slide-enter-from .detail-panel {
  transform: translateX(100%);
}

.panel-slide-leave-to .detail-panel {
  transform: translateX(100%);
}

/* ════════════ Responsive ════════════ */
@media (max-width: 900px) {
  .user-table-header,
  .user-row {
    grid-template-columns: 1fr 1fr;
    gap: 8px;
  }

  .th--email,
  .td--email,
  .th--login,
  .td--login {
    display: none;
  }

  .th--actions,
  .td--actions {
    justify-content: flex-start;
  }
}

@media (max-width: 640px) {
  .user-table-header,
  .user-row {
    grid-template-columns: 1fr auto;
  }

  .th--role,
  .td--role {
    display: none;
  }
}
</style>
