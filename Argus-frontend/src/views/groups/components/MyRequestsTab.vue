<script setup lang="ts">
import type { JoinRequestItem, MySentInvitationItem } from '@/api/group'
import EmptyState from '@/components/EmptyState.vue'

defineProps<{
  items: JoinRequestItem[]
  sentInvitations: MySentInvitationItem[]
}>()

function statusLabel(status: string): string {
  switch (status) {
    case 'PENDING': return '处理中'
    case 'APPROVED':
    case 'ACCEPTED': return '已通过/接受'
    case 'REJECTED': return '已拒绝'
    case 'CANCELED': return '已取消'
    default: return status
  }
}

function statusClass(status: string): string {
  switch (status) {
    case 'PENDING': return 'status-pending'
    case 'APPROVED':
    case 'ACCEPTED': return 'status-success'
    case 'REJECTED': return 'status-danger'
    case 'CANCELED': return 'status-muted'
    default: return ''
  }
}

function formatDate(dateStr?: string) {
  if (!dateStr || dateStr === 'null') return '-'
  const d = new Date(dateStr)
  if (!isNaN(d.getTime())) {
    const y = d.getFullYear()
    const m = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    const h = String(d.getHours()).padStart(2, '0')
    const min = String(d.getMinutes()).padStart(2, '0')
    return `${y}-${m}-${day} ${h}:${min}`
  }
  return dateStr
}
</script>

<template>
  <div class="requests-container">
    <div v-if="items.length === 0 && sentInvitations.length === 0">
      <EmptyState
        title="暂无申请或邀请记录"
        description="你还没有申请加入任何小组，也没有发出过邀请。点击上方按钮开始协作吧。"
      />
    </div>

    <div v-else class="list-content">
      <!-- Section: My Join Requests -->
      <div v-if="items.length > 0" class="section-block">
        <div class="section-header">
          <div class="section-icon blue">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
          <h3 class="section-title">我申请加入的小组</h3>
          <span class="count-badge">{{ items.length }}</span>
        </div>
        
        <div class="glass-table-container">
          <table class="glass-table">
            <thead>
              <tr>
                <th>目标小组</th>
                <th>小组编码</th>
                <th>申请时间</th>
                <th>当前状态</th>
                <th>处理反馈</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="req in items" :key="req.requestId">
                <td>
                  <div class="group-info">
                    <div class="group-avatar">{{ req.groupName.charAt(0) }}</div>
                    <span class="group-name">{{ req.groupName }}</span>
                  </div>
                </td>
                <td><code class="code-pill">{{ req.groupCode }}</code></td>
                <td class="time-cell">{{ formatDate(req.createdAt) }}</td>
                <td>
                  <span class="status-pill" :class="statusClass(req.status)">
                    {{ statusLabel(req.status) }}
                  </span>
                </td>
                <td class="time-cell">{{ req.decidedAt ? formatDate(req.decidedAt) : '等待审批中...' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Section: My Sent Invitations -->
      <div v-if="sentInvitations.length > 0" class="section-block">
        <div class="section-header">
          <div class="section-icon purple">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
              <circle cx="8.5" cy="7" r="4" />
              <line x1="20" y1="8" x2="20" y2="14" />
              <line x1="23" y1="11" x2="17" y2="11" />
            </svg>
          </div>
          <h3 class="section-title">我发出的成员邀请</h3>
          <span class="count-badge">{{ sentInvitations.length }}</span>
        </div>

        <div class="glass-table-container">
          <table class="glass-table">
            <thead>
              <tr>
                <th>被邀请人</th>
                <th>目标小组</th>
                <th>发送时间</th>
                <th>当前状态</th>
                <th>处理反馈</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="inv in sentInvitations" :key="inv.invitationId">
                <td>
                  <div class="user-info">
                    <div class="user-avatar">{{ inv.inviteeDisplayName.charAt(0) }}</div>
                    <span class="user-name">{{ inv.inviteeDisplayName }}</span>
                  </div>
                </td>
                <td class="group-name-cell">{{ inv.groupName }}</td>
                <td class="time-cell">{{ formatDate(inv.createdAt) }}</td>
                <td>
                  <span class="status-pill" :class="statusClass(inv.status)">
                    {{ statusLabel(inv.status) }}
                  </span>
                </td>
                <td class="time-cell">{{ inv.decidedAt ? formatDate(inv.decidedAt) : '对方尚未处理' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.requests-container {
  padding: 8px 4px;
  animation: fadeIn 0.5s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.list-content {
  display: flex;
  flex-direction: column;
  gap: 48px;
}

.section-block {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-left: 4px;
}

.section-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.section-icon svg {
  width: 20px;
  height: 20px;
}

.section-icon.blue {
  background: rgba(59, 130, 246, 0.1);
  color: #3b82f6;
}

.section-icon.purple {
  background: rgba(139, 92, 246, 0.1);
  color: #8b5cf6;
}

.section-title {
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
}

.count-badge {
  padding: 2px 10px;
  background: var(--surface-subtle);
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--text-secondary);
}

/* Glass Table Styles */
.glass-table-container {
  background: rgba(255, 255, 255, 0.5);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid var(--border-default);
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.03);
}

.glass-table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
}

.glass-table th {
  padding: 16px 24px;
  background: rgba(0, 0, 0, 0.02);
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--text-muted);
  border-bottom: 1px solid var(--border-subtle);
}

.glass-table td {
  padding: 18px 24px;
  font-size: 0.9rem;
  color: var(--text-secondary);
  border-bottom: 1px solid var(--border-subtle);
  transition: background 0.2s;
}

.glass-table tr:last-child td {
  border-bottom: none;
}

.glass-table tr:hover td {
  background: rgba(0, 0, 0, 0.01);
}

/* Cell Components */
.group-info, .user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.group-avatar, .user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: linear-gradient(135deg, #6366f1 0%, #a855f7 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 0.8rem;
}

.group-name, .user-name {
  font-weight: 600;
  color: var(--text-primary);
}

.code-pill {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  background: var(--surface-muted);
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 0.8rem;
  color: var(--text-secondary);
}

.time-cell {
  font-variant-numeric: tabular-nums;
  color: var(--text-muted);
  font-size: 0.85rem;
}

/* Status Pills */
.status-pill {
  display: inline-flex;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 0.75rem;
  font-weight: 600;
}

.status-pending {
  background: rgba(245, 158, 11, 0.1);
  color: #d97706;
}

.status-success {
  background: rgba(16, 185, 129, 0.1);
  color: #10b981;
}

.status-danger {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}

.status-muted {
  background: rgba(0, 0, 0, 0.05);
  color: var(--text-muted);
}
</style>
