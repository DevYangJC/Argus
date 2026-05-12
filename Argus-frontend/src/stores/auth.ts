import { defineStore } from 'pinia'
import {
  changePassword as changePasswordRequest,
  fetchCurrentUser,
  login as loginRequest,
  logout as logoutRequest,
  refreshSession,
  type ChangePasswordPayload,
  type CurrentUserProfile,
  type LoginPayload,
} from '../api/auth'
import http from '../api/http'

const ADMIN_HOME_PATH = '/app/admin/users'
const USER_HOME_PATH = '/app/groups'
const ACCOUNT_SECURITY_PATH = '/app/settings'
const LOGIN_PATH = '/login'
const BUSINESS_PATH_PREFIXES = ['/app/groups', '/app/documents', '/app/qa', '/app/assistant']

interface AuthState {
  accessToken: string | null
  currentUser: CurrentUserProfile | null
  isBootstrapping: boolean
  isAuthenticating: boolean
}

let bootstrapTask: Promise<CurrentUserProfile | null> | null = null

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    accessToken: null,
    currentUser: null,
    isBootstrapping: false,
    isAuthenticating: false,
  }),
  getters: {
    isAuthenticated(state): boolean {
      return state.accessToken !== null && state.currentUser !== null
    },
    isAdmin(state): boolean {
      return state.currentUser?.systemRole === 'ADMIN'
    },
    isUser(state): boolean {
      return state.currentUser?.systemRole === 'USER'
    },
    homePath(state): string {
      return resolveAuthorizedPath(state.currentUser)
    },
  },
  actions: {
    async bootstrap(force = false): Promise<CurrentUserProfile | null> {
      if (!force && this.currentUser !== null && this.accessToken !== null) {
        return this.currentUser
      }

      if (!force && bootstrapTask !== null) {
        return bootstrapTask
      }

      bootstrapTask = this.runBootstrap()
      try {
        return await bootstrapTask
      } finally {
        bootstrapTask = null
      }
    },
    async refresh(): Promise<CurrentUserProfile> {
      const session = await refreshSession()
      this.setSession(session.accessToken, session.currentUser)
      return session.currentUser
    },
    async login(payload: LoginPayload): Promise<CurrentUserProfile> {
      this.isAuthenticating = true
      try {
        const session = await loginRequest({
          loginId: payload.loginId.trim(),
          password: payload.password,
        })
        this.setSession(session.accessToken, session.currentUser)
        return session.currentUser
      } finally {
        this.isAuthenticating = false
      }
    },
    async logout(): Promise<void> {
      try {
        await logoutRequest()
      } catch {
        // 远端退出失败时仍清理本地会话，避免界面残留登录态。
      } finally {
        this.clearSession()
      }
    },
    async changePassword(payload: ChangePasswordPayload): Promise<CurrentUserProfile | null> {
      await changePasswordRequest(payload)

      if (this.currentUser !== null) {
        this.currentUser = { ...this.currentUser, mustChangePassword: false }
      }

      try {
        this.currentUser = await fetchCurrentUser()
      } catch {
        return this.currentUser
      }

      return this.currentUser
    },
    resolveLandingPath(requestedPath?: string | null): string {
      return resolveAuthorizedPath(this.currentUser, requestedPath)
    },
    resolveRedirectForPath(requestedPath: string): string | null {
      if (this.currentUser === null) {
        return LOGIN_PATH
      }

      return resolveRoleRedirect(this.currentUser, requestedPath)
    },
    clearSession() {
      this.accessToken = null
      this.currentUser = null
      applyAuthorizationHeader(null)
    },
    setSession(accessToken: string, currentUser: CurrentUserProfile) {
      this.accessToken = accessToken
      this.currentUser = currentUser
      applyAuthorizationHeader(accessToken)
    },
    async runBootstrap(): Promise<CurrentUserProfile | null> {
      this.isBootstrapping = true
      applyAuthorizationHeader(this.accessToken)

      try {
        if (this.accessToken !== null) {
          try {
            this.currentUser = await fetchCurrentUser()
            return this.currentUser
          } catch {
            return await this.refresh()
          }
        }

        return await this.refresh()
      } catch {
        this.clearSession()
        return null
      } finally {
        this.isBootstrapping = false
      }
    },
  },
})

export function resolveAuthorizedPath(
  user: CurrentUserProfile | null,
  requestedPath?: string | null,
): string {
  if (user === null) {
    return LOGIN_PATH
  }

  if (user.mustChangePassword) {
    return ACCOUNT_SECURITY_PATH
  }

  const safeRequestedPath = normalizeRequestedPath(requestedPath)

  if (user.systemRole === 'ADMIN') {
    return isAdminPath(safeRequestedPath) || safeRequestedPath === ACCOUNT_SECURITY_PATH
      ? safeRequestedPath
      : ADMIN_HOME_PATH
  }

  return isBusinessPath(safeRequestedPath) || safeRequestedPath === ACCOUNT_SECURITY_PATH
    ? safeRequestedPath
    : USER_HOME_PATH
}

export function resolveRoleRedirect(user: CurrentUserProfile, requestedPath: string): string | null {
  if (user.mustChangePassword && requestedPath !== ACCOUNT_SECURITY_PATH) {
    return ACCOUNT_SECURITY_PATH
  }

  if (user.systemRole === 'ADMIN' && isBusinessPath(requestedPath)) {
    return ADMIN_HOME_PATH
  }

  if (user.systemRole === 'USER' && isAdminPath(requestedPath)) {
    return USER_HOME_PATH
  }

  return null
}

function applyAuthorizationHeader(accessToken: string | null) {
  if (accessToken === null) {
    delete http.defaults.headers.common.Authorization
    return
  }

  http.defaults.headers.common.Authorization = `Bearer ${accessToken}`
}

function normalizeRequestedPath(path?: string | null): string | null {
  if (typeof path !== 'string' || path.length === 0) {
    return null
  }

  if (!path.startsWith('/') || path.startsWith('//') || path.startsWith(LOGIN_PATH)) {
    return null
  }

  return path
}

function isAdminPath(path: string | null): path is string {
  return path === '/app/admin' || path?.startsWith('/app/admin/') === true
}

function isBusinessPath(path: string | null): path is string {
  return BUSINESS_PATH_PREFIXES.some((prefix) => path === prefix || path?.startsWith(`${prefix}/`))
}
