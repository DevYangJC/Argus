<script setup lang="ts">
import type { GroupItem } from '@/api/group'
import GroupCard from './GroupCard.vue'
import EmptyState from '@/components/EmptyState.vue'

defineProps<{
  items: GroupItem[]
}>()

const emit = defineEmits<{
  manage: [groupId: number]
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
    <EmptyState
      v-if="items.length === 0"
      title="你还没有创建任何小组"
      description="点击上方「创建小组」创建你的第一个协作空间，创建后即可邀请成员、上传文档。"
    />

    <div v-else class="tab-card-grid">
      <GroupCard
        v-for="group in items"
        :key="group.groupId"
        :title="group.groupName"
        :description="group.description"
        :code="group.groupCode"
        :time="`创建于 ${formatDate(group.createdAt)}`"
        tagText="所有者"
        tagType="accent"
        @click="emit('manage', group.groupId)"
      >
        <template #meta v-if="group.pendingRequestCount && group.pendingRequestCount > 0">
          <div class="group-card__alert">
            <span class="alert-dot"></span>
            <span>有 {{ group.pendingRequestCount }} 条加入申请待处理</span>
          </div>
        </template>
      </GroupCard>
    </div>
  </div>
</template>

<style scoped>
/* Reuses styles from InvitationsTab — see that file for full style definitions */
.tab-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
}





.group-card__alert {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  padding: 6px 10px;
  border-radius: var(--radius-sm);
  background: rgba(239, 68, 68, 0.08);
  color: var(--el-color-danger);
  font-size: 0.82rem;
  font-weight: 600;
}

.alert-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--el-color-danger);
  animation: pulse-ring 2s infinite;
}

@keyframes pulse-ring {
  0% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.4); }
  70% { box-shadow: 0 0 0 4px rgba(239, 68, 68, 0); }
  100% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0); }
}

</style>
