<script setup lang="ts">
import { ref, watch } from 'vue'
import type { JoinRequestItem } from '@/api/group'

const props = defineProps<{
  visible: boolean
  loading: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  submit: [groupCode: string]
}>()

const code = ref('')
const error = ref('')

watch(
  () => props.visible,
  (v) => {
    if (v) {
      code.value = ''
      error.value = ''
    }
  },
)

function handleSubmit() {
  error.value = ''
  const trimmed = code.value.trim()
  if (trimmed.length === 0) {
    error.value = '请输入组织 ID'
    return
  }
  emit('submit', trimmed)
}

</script>

<template>
  <el-dialog
    :model-value="visible"
    width="640px"
    top="12vh"
    :close-on-click-modal="false"
    @update:model-value="(v: boolean) => { if (!v) emit('update:visible', false) }"
  >
    <template #header>
      <div class="modal-header">
        <h2 class="modal-header__title">加入小组</h2>
        <span class="modal-header__badge">申请</span>
      </div>
    </template>

    <div class="modal-body">
      <p v-if="error" class="modal-error">{{ error }}</p>

      <label class="form-field">
        <span class="form-field__label">组织 ID（groupCode）</span>
        <input
          v-model="code"
          type="text"
          maxlength="80"
          placeholder="例如：engineering-team"
          class="form-field__input"
          @keyup.enter="handleSubmit"
        />
      </label>
      <p class="form-hint">输入目标小组的组织 ID，向其所有者提交加入申请。对方审批通过后，该组会出现在「我加入的组」中。</p>
    </div>

    <template #footer>
      <el-button @click="emit('update:visible', false)">关闭</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">
        {{ loading ? '提交中...' : '提交申请' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
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
  background: rgba(245, 158, 11, 0.1);
  color: #d97706;
}

.modal-body {
  display: grid;
  gap: 12px;
  padding: 4px 0;
}

.modal-error {
  margin: 0;
  padding: 10px 14px;
  border-radius: var(--radius-sm);
  background: rgba(239, 68, 68, 0.08);
  color: var(--el-color-danger);
  font-size: 0.86rem;
  line-height: 1.5;
}

.form-field {
  display: grid;
  gap: 5px;
}

.form-field__label {
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--text-secondary);
}

.form-field__input {
  width: 100%;
  padding: 9px 12px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  font-size: 0.9rem;
  font-family: inherit;
  color: var(--text-primary);
  background: var(--surface-white);
  transition: border-color 0.15s ease;
}

.form-field__input:focus {
  outline: none;
  border-color: var(--brand-primary);
  box-shadow: 0 0 0 3px rgba(74, 144, 217, 0.1);
}

.form-hint {
  margin: 0;
  font-size: 0.8rem;
  color: var(--text-muted);
  line-height: 1.5;
}


</style>
