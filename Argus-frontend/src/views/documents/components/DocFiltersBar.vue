<script setup lang="ts">
import { computed } from 'vue'
import type { VisibleGroup } from '@/stores/app'

const props = defineProps<{
  groups: VisibleGroup[]
  selectedGroupId: number | null
  searchText: string
  statusFilter: string
  groupsLoading: boolean
  isGroupOwner: boolean
  hasGroupSelected: boolean
}>()

const emit = defineEmits<{
  'update:selectedGroupId': [val: number | null]
  'update:searchText': [val: string]
  'update:statusFilter': [val: string]
  'search-input': []
  'status-change': []
  'open-upload': []
}>()

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '待处理', value: 'PENDING' },
  { label: '处理中', value: 'PROCESSING' },
  { label: '就绪', value: 'READY' },
  { label: '失败', value: 'FAILED' },
]

const groupValue = computed({
  get: () => props.selectedGroupId,
  set: (v) => emit('update:selectedGroupId', v),
})

const searchValue = computed({
  get: () => props.searchText,
  set: (v) => emit('update:searchText', v),
})

const statusValue = computed({
  get: () => props.statusFilter,
  set: (v) => emit('update:statusFilter', v),
})
</script>

<template>
  <div class="filters-bar">
    <div class="filters-bar__left">
      <!-- Group selector -->
      <div class="filters-bar__field filters-bar__field--group">
        <label class="filters-bar__label">群组</label>
        <select
          v-model="groupValue"
          class="filters-bar__select"
          :disabled="groupsLoading"
        >
          <option :value="null" disabled>选择群组...</option>
          <option v-for="g in groups" :key="g.groupId" :value="g.groupId">
            {{ g.groupName }} ({{ g.relation === 'OWNER' ? '管理员' : '成员' }})
          </option>
        </select>
      </div>

      <!-- Search -->
      <div class="filters-bar__field">
        <label class="filters-bar__label">搜索</label>
        <div class="filters-bar__search">
          <svg class="filters-bar__search-icon" width="14" height="14" viewBox="0 0 24 24" fill="none">
            <circle cx="11" cy="11" r="7" stroke="currentColor" stroke-width="1.8" />
            <path d="M19 19L16 16" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
          </svg>
          <input
            v-model="searchValue"
            type="text"
            class="filters-bar__input"
            placeholder="按文件名查找..."
            @input="emit('search-input')"
          />
        </div>
      </div>

      <!-- Status filter -->
      <div class="filters-bar__field">
        <label class="filters-bar__label">状态</label>
        <select
          v-model="statusValue"
          class="filters-bar__select filters-bar__select--narrow"
          @change="emit('status-change')"
        >
          <option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </option>
        </select>
      </div>
    </div>

    <div class="filters-bar__right">
      <button
        v-if="isGroupOwner && hasGroupSelected"
        class="filters-bar__upload"
        @click="emit('open-upload')"
      >
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none">
          <path d="M12 19V5M5 12L12 5L19 12" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
        <span>上传文档</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.filters-bar {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
  padding: 16px 18px;
  margin-bottom: 18px;
  background: var(--surface-white);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
  flex-wrap: wrap;
}

.filters-bar__left {
  display: flex;
  align-items: flex-end;
  gap: 14px;
  flex-wrap: wrap;
}

.filters-bar__right {
  flex-shrink: 0;
}

.filters-bar__field {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.filters-bar__label {
  font-size: 0.7rem;
  font-weight: 700;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.1em;
  padding-left: 2px;
}

.filters-bar__select,
.filters-bar__input {
  padding: 8px 12px;
  font-size: 0.88rem;
  font-family: inherit;
  color: var(--text-primary);
  background: var(--surface-subtle);
  border: 1.5px solid transparent;
  border-radius: var(--radius-sm);
  outline: none;
  transition: all 0.18s ease;
}

.filters-bar__select {
  padding-right: 30px;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg width='10' height='6' viewBox='0 0 10 6' fill='none' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath d='M1 1L5 5L9 1' stroke='%2394a3b8' stroke-width='1.5' stroke-linecap='round'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 10px center;
  cursor: pointer;
}

.filters-bar__select:focus,
.filters-bar__input:focus {
  background: #fff;
  border-color: var(--brand-primary);
  box-shadow: 0 0 0 3px rgba(74, 144, 217, 0.1);
}

.filters-bar__select:hover:not(:disabled),
.filters-bar__input:hover {
  background: #fff;
  border-color: var(--border-default);
}

.filters-bar__select:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.filters-bar__field--group .filters-bar__select {
  min-width: 220px;
}

.filters-bar__select--narrow {
  min-width: 120px;
}

/* Search input */
.filters-bar__search {
  position: relative;
  display: flex;
  align-items: center;
}

.filters-bar__search-icon {
  position: absolute;
  left: 12px;
  color: var(--text-muted);
  pointer-events: none;
}

.filters-bar__input {
  padding-left: 34px;
  width: 220px;
}

.filters-bar__input::placeholder {
  color: #94a3b8;
}

/* Upload button */
.filters-bar__upload {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 9px 20px;
  font-family: inherit;
  font-size: 0.88rem;
  font-weight: 600;
  letter-spacing: 0.01em;
  color: #fff;
  background: linear-gradient(135deg, var(--brand-primary), var(--brand-primary-dark));
  border: none;
  border-radius: var(--radius-sm);
  box-shadow: 0 4px 14px rgba(74, 144, 217, 0.25);
  cursor: pointer;
  transition: all 0.2s ease;
}

.filters-bar__upload:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 20px rgba(74, 144, 217, 0.35);
}

.filters-bar__upload:active {
  transform: translateY(0);
}

@media (max-width: 768px) {
  .filters-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .filters-bar__left {
    width: 100%;
  }

  .filters-bar__right {
    width: 100%;
  }

  .filters-bar__upload {
    width: 100%;
    justify-content: center;
  }

  .filters-bar__field--group .filters-bar__select {
    min-width: 150px;
  }

  .filters-bar__input {
    width: 160px;
  }
}
</style>
