<script setup lang="ts">
import { ref } from 'vue'
import { changePassword } from '@/api/auth'
import { extractApiError } from '@/api/http'

const props = defineProps<{
  inline?: boolean
}>()

const emit = defineEmits<{
  completed: []
}>()

const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const submitting = ref(false)
const error = ref('')
const success = ref('')

async function handleSubmit() {
  error.value = ''
  success.value = ''

  if (!currentPassword.value || !newPassword.value || !confirmPassword.value) {
    error.value = '请填写所有字段'
    return
  }

  if (newPassword.value.length < 6) {
    error.value = '新密码长度不能少于6位'
    return
  }

  if (newPassword.value !== confirmPassword.value) {
    error.value = '两次输入的新密码不一致'
    return
  }

  submitting.value = true
  try {
    await changePassword({
      currentPassword: currentPassword.value,
      newPassword: newPassword.value,
    })
    success.value = '密码修改成功'
    currentPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
    setTimeout(() => emit('completed'), 1200)
  } catch (err) {
    error.value = extractApiError(err, '密码修改失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="account-password-form" :class="{ 'account-password-form--inline': inline }">
    <div v-if="success" class="form-feedback form-feedback--success">{{ success }}</div>
    <div v-if="error" class="form-feedback form-feedback--error">{{ error }}</div>

    <div class="form-fields">
      <label class="form-field">
        <span>当前密码</span>
        <input
          v-model="currentPassword"
          type="password"
          autocomplete="current-password"
          placeholder="输入当前密码"
        />
      </label>
      <label class="form-field">
        <span>新密码</span>
        <input
          v-model="newPassword"
          type="password"
          autocomplete="new-password"
          placeholder="输入新密码（至少6位）"
        />
      </label>
      <label class="form-field">
        <span>确认新密码</span>
        <input
          v-model="confirmPassword"
          type="password"
          autocomplete="new-password"
          placeholder="再次输入新密码"
        />
      </label>
    </div>

    <button
      class="btn-submit"
      :disabled="submitting"
      type="button"
      @click="handleSubmit"
    >
      {{ submitting ? '提交中...' : '修改密码' }}
    </button>
  </div>
</template>

<style scoped>
.account-password-form {
  display: grid;
  gap: 16px;
  padding: 20px;
  background: var(--surface-white);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
}

.account-password-form--inline {
  border: none;
  padding: 0;
  background: transparent;
}

.form-feedback {
  padding: 10px 14px;
  border-radius: var(--radius-sm);
  font-size: 0.88rem;
  line-height: 1.5;
}

.form-feedback--success {
  background: rgba(16, 185, 129, 0.08);
  color: #10b981;
}

.form-feedback--error {
  background: rgba(239, 68, 68, 0.08);
  color: #ef4444;
}

.form-fields {
  display: grid;
  gap: 14px;
}

.form-field {
  display: grid;
  gap: 5px;
}

.form-field span {
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--text-secondary);
}

.form-field input {
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

.form-field input:focus {
  outline: none;
  border-color: var(--brand-primary);
  box-shadow: 0 0 0 3px rgba(74, 144, 217, 0.1);
}

.btn-submit {
  justify-self: start;
  padding: 9px 22px;
  border-radius: var(--radius-sm);
  background: var(--brand-primary);
  color: #fff;
  font-size: 0.9rem;
  font-weight: 600;
  transition: all 0.15s ease;
}

.btn-submit:hover:not(:disabled) {
  background: var(--brand-primary-dark);
}

.btn-submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
