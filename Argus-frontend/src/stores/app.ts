import { defineStore } from 'pinia'
import type { GroupItem, PendingInvitationItem } from '../api/group'

export type GroupRelation = 'OWNER' | 'MEMBER'

export interface VisibleGroup extends GroupItem {
  relation: GroupRelation
}

interface AppState {
  currentGroupId: number | null
  ownedGroups: GroupItem[]
  joinedGroups: GroupItem[]
  pendingInvitations: PendingInvitationItem[]
  isGroupsLoading: boolean
}

export const useAppStore = defineStore('app', {
  state: (): AppState => ({
    currentGroupId: null,
    ownedGroups: [],
    joinedGroups: [],
    pendingInvitations: [],
    isGroupsLoading: false,
  }),
  getters: {
    visibleGroups(state): VisibleGroup[] {
      return [
        ...state.ownedGroups.map((group) => ({ ...group, relation: 'OWNER' as const })),
        ...state.joinedGroups.map((group) => ({ ...group, relation: 'MEMBER' as const })),
      ]
    },
    currentGroup(state): VisibleGroup | null {
      const visibleGroups = [
        ...state.ownedGroups.map((group) => ({ ...group, relation: 'OWNER' as const })),
        ...state.joinedGroups.map((group) => ({ ...group, relation: 'MEMBER' as const })),
      ]
      return visibleGroups.find((group) => group.groupId === state.currentGroupId) ?? null
    },
    canManageCurrentGroup(): boolean {
      return this.currentGroup?.relation === 'OWNER'
    },
  },
  actions: {
    setCurrentGroupId(groupId: number | null) {
      this.currentGroupId = groupId
    },
    setGroupCollections(payload: {
      ownedGroups: GroupItem[]
      joinedGroups: GroupItem[]
      pendingInvitations: PendingInvitationItem[]
    }) {
      this.ownedGroups = payload.ownedGroups
      this.joinedGroups = payload.joinedGroups
      this.pendingInvitations = payload.pendingInvitations
    },
    applyGroupQueryResult(payload: {
      ownedGroups: GroupItem[]
      joinedGroups: GroupItem[]
      pendingInvitations: PendingInvitationItem[]
    }) {
      this.setGroupCollections(payload)
      const visibleGroupIds = new Set([
        ...payload.ownedGroups.map((group) => group.groupId),
        ...payload.joinedGroups.map((group) => group.groupId),
      ])
      if (visibleGroupIds.size === 0) {
        this.currentGroupId = null
        return
      }
      if (this.currentGroupId !== null && visibleGroupIds.has(this.currentGroupId)) {
        return
      }
      this.currentGroupId = payload.ownedGroups[0]?.groupId ?? payload.joinedGroups[0]?.groupId ?? null
    },
    resetGroupContext(isLoading = false) {
      this.currentGroupId = null
      this.ownedGroups = []
      this.joinedGroups = []
      this.pendingInvitations = []
      this.isGroupsLoading = isLoading
    },
    setGroupsLoading(isLoading: boolean) {
      this.isGroupsLoading = isLoading
    },
  },
})
