<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { register as registerApi } from '@/api/auth'
import { extractApiError } from '@/api/http'
import type { LoginPayload, RegisterPayload } from '@/api/auth'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success'): void
}>()

const router = useRouter()
const authStore = useAuthStore()

const mode = ref<'login' | 'register'>('login')

const loginForm = reactive<LoginPayload>({
  loginId: '',
  password: '',
})

const registerForm = reactive<RegisterPayload>({
  username: '',
  email: '',
  displayName: '',
  password: '',
})

const loading = ref(false)
const errorMsg = ref('')

function switchMode(target: 'login' | 'register') {
  mode.value = target
  errorMsg.value = ''
}

watch(() => props.visible, (val) => {
  if (!val) {
    errorMsg.value = ''
    loginForm.loginId = ''
    loginForm.password = ''
    registerForm.username = ''
    registerForm.email = ''
    registerForm.displayName = ''
    registerForm.password = ''
  }
})

async function handleLogin() {
  if (!loginForm.loginId.trim() || !loginForm.password.trim()) {
    errorMsg.value = '请输入登录标识和密码'
    return
  }
  errorMsg.value = ''
  loading.value = true
  try {
    await authStore.login({
      loginId: loginForm.loginId.trim(),
      password: loginForm.password,
    })
    emit('update:visible', false)
    emit('success')
    router.push(authStore.resolveLandingPath())
  } catch (err) {
    errorMsg.value = extractApiError(err, '登录失败')
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  if (!registerForm.username.trim() || !registerForm.email.trim() || !registerForm.password.trim()) {
    errorMsg.value = '请填写必填字段'
    return
  }
  errorMsg.value = ''
  loading.value = true
  try {
    await registerApi({
      username: registerForm.username.trim(),
      email: registerForm.email.trim(),
      displayName: registerForm.displayName.trim() || registerForm.username.trim(),
      password: registerForm.password,
    })
    await authStore.login({
      loginId: registerForm.username.trim(),
      password: registerForm.password,
    })
    emit('update:visible', false)
    emit('success')
    router.push(authStore.resolveLandingPath())
  } catch (err) {
    errorMsg.value = extractApiError(err, '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :show-close="true"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    width="420px"
    top="15vh"
    @update:model-value="(val: boolean) => emit('update:visible', val)"
  >
    <template #header>
      <div class="modal-header">
        <h2>{{ mode === 'login' ? '欢迎回来' : '创建账号' }}</h2>
        <p>{{ mode === 'login' ? '登录数据洞察中心' : '立即注册，开启智能知识服务' }}</p>
      </div>
    </template>

    <!-- Toggle tabs -->
    <div class="mode-tabs">
      <button
        class="mode-tab"
        :class="{ active: mode === 'login' }"
        @click="switchMode('login')"
      >登录</button>
      <button
        class="mode-tab"
        :class="{ active: mode === 'register' }"
        @click="switchMode('register')"
      >注册</button>
    </div>

    <!-- Login form -->
    <form v-if="mode === 'login'" class="modal-form" @submit.prevent="handleLogin">
      <div class="input-group">
        <label>登录标识</label>
        <input
          v-model="loginForm.loginId"
          type="text"
          placeholder="用户名或邮箱"
          autocomplete="username"
        />
      </div>
      <div class="input-group">
        <label>密码</label>
        <input
          v-model="loginForm.password"
          type="password"
          placeholder="输入密码"
          autocomplete="current-password"
        />
      </div>
      <p v-if="errorMsg" class="form-error">{{ errorMsg }}</p>
      <button type="submit" class="btn-submit" :disabled="loading">
        <span v-if="!loading">登录</span>
        <span v-else>登录中...</span>
      </button>
    </form>

    <!-- Register form -->
    <form v-else class="modal-form" @submit.prevent="handleRegister">
      <div class="input-group">
        <label>用户名 <span class="required">*</span></label>
        <input
          v-model="registerForm.username"
          type="text"
          placeholder="字母、数字、下划线"
          autocomplete="username"
        />
      </div>
      <div class="input-group">
        <label>邮箱 <span class="required">*</span></label>
        <input
          v-model="registerForm.email"
          type="email"
          placeholder="your@email.com"
          autocomplete="email"
        />
      </div>
      <div class="input-group">
        <label>显示名称</label>
        <input
          v-model="registerForm.displayName"
          type="text"
          placeholder="您的称呼（可选）"
        />
      </div>
      <div class="input-group">
        <label>密码 <span class="required">*</span></label>
        <input
          v-model="registerForm.password"
          type="password"
          placeholder="设置密码"
          autocomplete="new-password"
        />
      </div>
      <p v-if="errorMsg" class="form-error">{{ errorMsg }}</p>
      <button type="submit" class="btn-submit" :disabled="loading">
        <span v-if="!loading">注册</span>
        <span v-else>注册中...</span>
      </button>
    </form>
  </el-dialog>
</template>

<style scoped>
/* Header */
.modal-header {
  text-align: center;
  padding-top: 8px;
}
.modal-header h2 {
  font-family: 'Poppins', 'Noto Sans SC', sans-serif;
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 4px;
}
.modal-header p {
  font-size: 13.5px;
  color: var(--text-secondary);
  margin: 0;
}

/* Mode toggle tabs */
.mode-tabs {
  display: flex;
  background: var(--surface-muted);
  border-radius: var(--radius-sm);
  padding: 3px;
  margin: 20px 0 16px;
}
.mode-tab {
  flex: 1;
  padding: 8px 0;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary);
  background: transparent;
  transition: all 0.2s ease;
}
.mode-tab.active {
  background: var(--surface-white);
  color: var(--text-primary);
  box-shadow: var(--shadow-xs);
  font-weight: 600;
}
.mode-tab:not(.active):hover {
  color: var(--text-primary);
}

/* Form */
.modal-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.input-group {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.input-group label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}
.required {
  color: var(--el-color-danger);
}
.input-group input {
  padding: 10px 13px;
  border: 1.5px solid var(--border-default);
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-family: inherit;
  color: var(--text-primary);
  background: var(--surface-white);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
  outline: none;
}
.input-group input::placeholder {
  color: #94a3b8;
}
.input-group input:focus {
  border-color: var(--brand-primary);
  box-shadow: 0 0 0 3px rgba(74, 144, 217, 0.1);
}

.form-error {
  font-size: 13px;
  color: var(--el-color-danger);
  margin: 0;
}

.btn-submit {
  padding: 12px 24px;
  border-radius: var(--radius-sm);
  background: linear-gradient(135deg, var(--brand-primary), var(--brand-primary-dark));
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  transition: all 0.2s ease;
  box-shadow: 0 4px 14px rgba(74, 144, 217, 0.25);
  margin-top: 4px;
}
.btn-submit:hover:not(:disabled) {
  box-shadow: 0 6px 20px rgba(74, 144, 217, 0.35);
  transform: translateY(-1px);
}
.btn-submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
