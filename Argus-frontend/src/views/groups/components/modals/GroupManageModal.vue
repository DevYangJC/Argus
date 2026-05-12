<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { GroupItem, GroupMemberItem, OwnerJoinRequestItem } from '@/api/group'

const props = defineProps<{
  visible: boolean
  mode: 'owner' | 'member'
  group: GroupItem | null
  members: GroupMemberItem[]
  requests: OwnerJoinRequestItem[]
  isMembersLoading: boolean
  isRequestsLoading: boolean
  isInviting: boolean
  removingKeys: Set<string>
  requestActionIds: Set<number>
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  removeMember: [userId: number]
  approveRequest: [requestId: number]
  rejectRequest: [requestId: number]
  inviteMember: [userId: number]
  leaveGroup: []
}>()

const activeSection = ref<'members' | 'requests'>('members')
const inviteUserId = ref('')
const inviteError = ref('')

function handleInvite() {
  const parsed = Number(inviteUserId.value)
  if (!Number.isInteger(parsed) || parsed <= 0) {
    inviteError.value = '请输入合法的用户 ID'
    return
  }
  inviteError.value = ''
  emit('inviteMember', parsed)
  inviteUserId.value = ''
}

function ownerName(userId: number, members: GroupMemberItem[]): string {
  const owner = members.find((m) => m.userId === userId && m.role === 'OWNER')
  return owner?.displayName ?? `用户 #${userId}`
}

async function copyGroupCode(code: string) {
  try {
    await navigator.clipboard.writeText(code)
    ElMessage.success('组织 ID 已复制到剪贴板')
  } catch (err) {
    ElMessage.error('复制失败，请手动复制')
  }
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    width="640px"
    top="8vh"
    :close-on-click-modal="false"
    @update:model-value="(v: boolean) => { if (!v) emit('update:visible', false) }"
  >
    <template #header>
      <div class="modal-header">
        <h2 class="modal-header__title">{{ group?.groupName ?? '小组详情' }}</h2>
        <span
          class="modal-header__badge"
          :class="mode === 'owner' ? 'modal-header__badge--owner' : 'modal-header__badge--member'"
        >
          {{ mode === 'owner' ? '所有者' : '成员' }}
        </span>
      </div>
    </template>

    <div class="modal-body">
      <!-- Group info -->
      <div v-if="group" class="info-bar">
        <div class="info-bar__item">
          <span class="info-bar__label">组织 ID</span>
          <div class="info-bar__value-wrap">
            <code class="info-bar__value">{{ group.groupCode }}</code>
            <el-button type="primary" link size="small" @click="copyGroupCode(group.groupCode)" title="复制">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
              </svg>
            </el-button>
          </div>
        </div>
        <div class="info-bar__item">
          <span class="info-bar__label">内部 ID</span>
          <span class="info-bar__value">#{{ group.groupId }}</span>
        </div>
        <div class="info-bar__item">
          <span class="info-bar__label">角色</span>
          <span class="info-bar__value">{{ mode === 'owner' ? '所有者' : '成员' }}</span>
        </div>
      </div>

      <!-- ── OWNER VIEW ── -->
      <template v-if="mode === 'owner'">
        <!-- Invite member -->
        <section class="manage-section">
          <h3 class="manage-section__title">邀请成员</h3>
          <div class="invite-row">
            <input
              v-model="inviteUserId"
              type="number"
              min="1"
              placeholder="输入用户 ID，例如：1003"
              class="form-input"
              @keyup.enter="handleInvite"
            />
            <el-button type="primary" :loading="props.isInviting" size="small" @click="handleInvite">
              {{ props.isInviting ? '邀请中...' : '发起邀请' }}
            </el-button>
          </div>
          <p v-if="inviteError" class="field-error">{{ inviteError }}</p>
          <p class="field-hint">被邀请用户可在右上角头像菜单查看自己的用户 ID，将编号发给你即可。</p>
        </section>

        <!-- Section tabs -->
        <div class="section-tabs">
          <button
            class="section-tab"
            :class="{ 'is-active': activeSection === 'members' }"
            type="button"
            @click="activeSection = 'members'"
          >
            成员管理
            <span class="section-tab__count">{{ props.members.length }}</span>
          </button>
          <button
            class="section-tab"
            :class="{ 'is-active': activeSection === 'requests' }"
            type="button"
            @click="activeSection = 'requests'"
          >
            加入申请
            <span
              v-if="props.requests.length > 0"
              class="section-tab__count section-tab__count--pending"
            >
              {{ props.requests.length }}
            </span>
          </button>
        </div>

        <!-- Members list -->
        <div v-if="activeSection === 'members'" class="section-content">
          <p v-if="props.isMembersLoading" class="placeholder-text">正在加载成员列表...</p>
          <p v-else-if="props.members.length === 0" class="placeholder-text">暂无成员</p>
          <ul v-else class="member-list">
            <li v-for="m in props.members" :key="m.userId" class="member-item">
              <div class="member-item__info">
                <span class="member-item__avatar">{{ m.displayName.charAt(0) }}</span>
                <div>
                  <strong>{{ m.displayName }}</strong>
                  <span>ID: {{ m.userId }} · {{ m.userCode }}</span>
                </div>
              </div>
              <div class="member-item__meta">
                <span v-if="m.role === 'OWNER'" class="pill pill--owner">所有者</span>
                <button
                  v-else
                  class="btn-remove"
                  :disabled="props.removingKeys.has(`${group!.groupId}:${m.userId}`)"
                  type="button"
                  @click="emit('removeMember', m.userId)"
                >
                  {{ props.removingKeys.has(`${group!.groupId}:${m.userId}`) ? '移除中...' : '移除' }}
                </button>
              </div>
            </li>
          </ul>
        </div>

        <!-- Join requests list -->
        <div v-if="activeSection === 'requests'" class="section-content">
          <p v-if="props.isRequestsLoading" class="placeholder-text">正在加载申请列表...</p>
          <p v-else-if="props.requests.length === 0" class="placeholder-text">当前没有待审批的加入申请</p>
          <ul v-else class="member-list">
            <li v-for="req in props.requests" :key="req.requestId" class="member-item">
              <div class="member-item__info">
                <span class="member-item__avatar">{{ req.applicantDisplayName.charAt(0) }}</span>
                <div>
                  <strong>{{ req.applicantDisplayName }}</strong>
                  <span>{{ req.applicantUserCode }} · {{ new Date(req.createdAt).toLocaleString() }}</span>
                </div>
              </div>
              <div class="member-item__actions">
                <button
                  class="btn-accept-sm"
                  :disabled="props.requestActionIds.has(req.requestId)"
                  type="button"
                  @click="emit('approveRequest', req.requestId)"
                >
                  通过
                </button>
                <button
                  class="btn-reject-sm"
                  :disabled="props.requestActionIds.has(req.requestId)"
                  type="button"
                  @click="emit('rejectRequest', req.requestId)"
                >
                  拒绝
                </button>
              </div>
            </li>
          </ul>
        </div>
      </template>

      <!-- ── MEMBER VIEW ── -->
      <template v-if="mode === 'member'">
        <div class="member-notice">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="1.5"/>
            <path d="M12 8V12M12 16H12.01" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
          <div>
            <p>你当前是成员，仅可查看该小组内容。</p>
            <p>如需管理成员或审批申请，请联系该组的所有者。</p>
          </div>
        </div>

        <div class="leave-action">
          <el-button type="danger" plain @click="emit('leaveGroup')">退出该小组</el-button>
        </div>
      </template>
    </div>
  </el-dialog>
</template>

<style scoped>
/* Header */
.modal-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.modal-header__title {
  font-family: 'Poppins', 'Noto Sans SC', sans-serif;
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
}

.modal-header__badge {
  padding: 2px 9px;
  border-radius: 999px;
  font-size: 0.7rem;
  font-weight: 700;
}

.modal-header__badge--owner {
  background: rgba(74, 144, 217, 0.1);
  color: var(--brand-primary);
}

.modal-header__badge--member {
  background: rgba(92, 201, 193, 0.12);
  color: #3da89f;
}

/* Body */
.modal-body {
  display: grid;
  gap: 16px;
  padding: 4px 0;
}

/* Info bar */
.info-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding: 12px 16px;
  background: var(--surface-subtle);
  border-radius: var(--radius-sm);
}

.info-bar__item {
  display: grid;
  gap: 2px;
}

.info-bar__label {
  font-size: 0.7rem;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.info-bar__value {
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--text-primary);
  font-family: 'JetBrains Mono', monospace;
}

.info-bar__value-wrap {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* Manage sections */
.manage-section {
  display: grid;
  gap: 8px;
}

.manage-section__title {
  margin: 0;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-primary);
}

.invite-row {
  display: flex;
  gap: 8px;
}

.form-input {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  font-size: 0.88rem;
  font-family: inherit;
  color: var(--text-primary);
  background: var(--surface-white);
  transition: border-color 0.15s ease;
}

.form-input:focus {
  outline: none;
  border-color: var(--brand-primary);
  box-shadow: 0 0 0 3px rgba(74, 144, 217, 0.1);
}

.field-error {
  margin: 0;
  font-size: 0.8rem;
  color: var(--el-color-danger);
}

.field-hint {
  margin: 0;
  font-size: 0.78rem;
  color: var(--text-muted);
  line-height: 1.5;
}

/* Section tabs */
.section-tabs {
  display: flex;
  gap: 0;
  border-bottom: 2px solid var(--border-default);
}

.section-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: none;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 0.84rem;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.15s ease;
}

.section-tab:hover {
  color: var(--text-primary);
}

.section-tab.is-active {
  color: var(--brand-primary);
  border-bottom-color: var(--brand-primary);
}

.section-tab__count {
  padding: 1px 6px;
  border-radius: 999px;
  font-size: 0.7rem;
  background: var(--surface-muted);
  color: var(--text-secondary);
}

.section-tab__count--pending {
  background: rgba(245, 158, 11, 0.12);
  color: #d97706;
}

.section-content {
  min-height: 80px;
}

.placeholder-text {
  margin: 0;
  padding: 16px 0;
  font-size: 0.84rem;
  color: var(--text-muted);
  font-style: italic;
}

/* Member list */
.member-list {
  display: grid;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.member-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  background: var(--surface-white);
}

.member-item__info {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.member-item__avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--brand-primary), var(--brand-accent));
  color: #fff;
  font-size: 0.78rem;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.member-item__info strong {
  display: block;
  font-size: 0.86rem;
  color: var(--text-primary);
}

.member-item__info span {
  font-size: 0.72rem;
  color: var(--text-muted);
}

.member-item__meta,
.member-item__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.pill {
  padding: 2px 7px;
  border-radius: 999px;
  font-size: 0.68rem;
  font-weight: 700;
}

.pill--owner {
  background: rgba(74, 144, 217, 0.1);
  color: var(--brand-primary);
}

.btn-remove {
  padding: 4px 10px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-xs);
  background: var(--surface-white);
  color: var(--el-color-danger);
  font-size: 0.76rem;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-remove:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.08);
}

.btn-remove:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-accept-sm {
  padding: 4px 12px;
  border: none;
  border-radius: var(--radius-xs);
  background: var(--brand-primary);
  color: #fff;
  font-size: 0.76rem;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-accept-sm:hover:not(:disabled) {
  background: var(--brand-primary-dark);
}

.btn-accept-sm:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn-reject-sm {
  padding: 4px 10px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-xs);
  background: var(--surface-white);
  color: var(--text-secondary);
  font-size: 0.76rem;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-reject-sm:hover:not(:disabled) {
  border-color: var(--el-color-danger);
  color: var(--el-color-danger);
}

.btn-reject-sm:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* Member notice */
.member-notice {
  display: flex;
  gap: 12px;
  padding: 14px 16px;
  border-radius: var(--radius-sm);
  background: rgba(92, 201, 193, 0.06);
  color: var(--text-secondary);
  border: 1px solid rgba(92, 201, 193, 0.15);
}

.member-notice svg {
  flex-shrink: 0;
  color: var(--brand-accent-dark);
}

.member-notice p {
  margin: 0;
  font-size: 0.84rem;
  line-height: 1.5;
}

.leave-action {
  padding-top: 4px;
}
</style>
