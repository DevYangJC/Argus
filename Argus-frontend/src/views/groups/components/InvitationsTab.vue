<script setup lang="ts">
import type { PendingInvitationItem } from '@/api/group'
import GroupCard from './GroupCard.vue'
import EmptyState from '@/components/EmptyState.vue'

defineProps<{
  items: PendingInvitationItem[]
  actionIds: Set<number>
}>()

const emit = defineEmits<{
  accept: [id: number]
  reject: [id: number]
}>()

function formatDate(dateStr?: string) {
  if (!dateStr) return '未知时间'
  const d = new Date(dateStr)
  if (!isNaN(d.getTime())) {
    return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`
  }
  return String(dateStr).split(' ')[0].split('T')[0].replace(/-/g, '.')
}
</script>

<template>
  <div>
    <!-- Loading skeleton -->
    <div v-if="false" class="tab-card-grid">
      <div v-for="n in 3" :key="n" class="card-skeleton"></div>
    </div>

    <!-- Empty -->
    <EmptyState
      v-else-if="items.length === 0"
      title="暂无待处理邀请"
      description="当其他用户邀请你加入小组时，邀请会显示在这里。你也可以主动点击上方「加入小组」提交申请。"
    />

    <!-- Card grid -->
    <div v-else class="tab-card-grid">
      <GroupCard
        v-for="invitation in items"
        :key="invitation.invitationId"
        :title="invitation.groupName"
        :description="`邀请人：${invitation.inviterDisplayName}`"
        :time="`创建于 ${formatDate((invitation as any).createdAt)}`"
        tagText="待处理"
        tagType="warning"
      >
        <template #meta>
          <p class="minimal-card__inviter">
            邀请人：<strong>{{ invitation.inviterDisplayName }}</strong>
          </p>
        </template>
        
        <template #actions>
          <button
            class="action-btn action-btn--primary"
            type="button"
            :disabled="actionIds.has(invitation.invitationId)"
            @click.stop="emit('accept', invitation.invitationId)"
          >
            接受
          </button>
          <button
            class="action-btn action-btn--danger"
            type="button"
            :disabled="actionIds.has(invitation.invitationId)"
            @click.stop="emit('reject', invitation.invitationId)"
          >
            拒绝
          </button>
        </template>
      </GroupCard>
    </div>
  </div>
</template>

<style scoped>
.tab-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
}

.card-skeleton {
  min-height: 140px;
  border-radius: var(--radius-md);
  background: linear-gradient(90deg, var(--surface-muted) 25%, var(--surface-subtle) 50%, var(--surface-muted) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}


.minimal-card__inviter {
  margin: 8px 0 0;
  font-size: 0.85rem;
  color: var(--text-muted);
}

.action-btn {
  padding: 8px 20px;
  border-radius: var(--radius-sm);
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid transparent;
}

.action-btn--primary {
  background: var(--brand-primary);
  color: #ffffff;
}

.action-btn--primary:hover {
  background: var(--brand-primary-dark);
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.2);
}

.action-btn--primary:disabled {
  background: var(--surface-muted);
  color: var(--text-muted);
  cursor: not-allowed;
  box-shadow: none;
}

.action-btn--danger {
  background: transparent;
  border: 1px solid var(--border-default);
  color: var(--text-secondary);
}

.action-btn--danger:hover {
  border-color: var(--el-color-danger);
  color: var(--el-color-danger);
  background: rgba(239, 68, 68, 0.05);
}

.action-btn--danger:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

</style>
