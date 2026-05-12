<script setup lang="ts">
defineProps<{
  ownedCount: number
  joinedCount: number
  invitationCount: number
  requestCount: number
  activeTab: string
}>()

const emit = defineEmits<{
  'select-tab': [tab: string]
}>()
</script>

<template>
  <div class="stats-bar">
    <!-- Owned -->
    <button
      class="stats-bar__card"
      :class="{ 'is-active': activeTab === 'owned' }"
      type="button"
      @click="emit('select-tab', 'owned')"
    >
      <div class="stats-card__top">
        <span class="stats-bar__label">我拥有的组</span>
        <div class="stats-icon stats-icon--blue">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="m2 4 3 12h14l3-12-6 7-4-7-4 7-6-7zm1 16h18"/>
          </svg>
        </div>
      </div>
      <div class="stats-card__bottom">
        <span class="stats-bar__num">{{ ownedCount }}</span>
      </div>
    </button>

    <!-- Joined -->
    <button
      class="stats-bar__card"
      :class="{ 'is-active': activeTab === 'joined' }"
      type="button"
      @click="emit('select-tab', 'joined')"
    >
      <div class="stats-card__top">
        <span class="stats-bar__label">我加入的组</span>
        <div class="stats-icon stats-icon--green">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/>
            <circle cx="9" cy="7" r="4"/>
            <path d="M22 21v-2a4 4 0 0 0-3-3.87"/>
            <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
          </svg>
        </div>
      </div>
      <div class="stats-card__bottom">
        <span class="stats-bar__num">{{ joinedCount }}</span>
      </div>
    </button>

    <!-- Invitations -->
    <button
      class="stats-bar__card stats-bar__card--pending"
      :class="{ 'is-active': activeTab === 'invitations' }"
      type="button"
      @click="emit('select-tab', 'invitations')"
    >
      <div class="stats-card__top">
        <span class="stats-bar__label">
          待处理邀请
          <span v-if="invitationCount > 0" class="stats-bar__dot"></span>
        </span>
        <div class="stats-icon stats-icon--amber">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="22 12 16 12 14 15 10 15 8 12 2 12"/>
            <path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z"/>
          </svg>
        </div>
      </div>
      <div class="stats-card__bottom">
        <span class="stats-bar__num">{{ invitationCount }}</span>
      </div>
    </button>

    <!-- Requests -->
    <button
      class="stats-bar__card"
      :class="{ 'is-active': activeTab === 'requests' }"
      type="button"
      @click="emit('select-tab', 'requests')"
    >
      <div class="stats-card__top">
        <span class="stats-bar__label">我的申请</span>
        <div class="stats-icon stats-icon--purple">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="22" y1="2" x2="11" y2="13"/>
            <polygon points="22 2 15 22 11 13 2 9 22 2"/>
          </svg>
        </div>
      </div>
      <div class="stats-card__bottom">
        <span class="stats-bar__num">{{ requestCount }}</span>
      </div>
    </button>
  </div>
</template>

<style scoped>
.stats-bar {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.stats-bar__card {
  display: flex;
  flex-direction: column;
  padding: 20px 24px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  background: var(--surface-white);
  text-align: left;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
  box-shadow: 0 2px 8px rgba(0,0,0,0.02);
  font-family: inherit;
  outline: none;
}

.stats-bar__card:hover {
  border-color: rgba(99, 102, 241, 0.3);
  box-shadow: 0 10px 24px rgba(0,0,0,0.06);
  transform: translateY(-2px);
}

.stats-bar__card.is-active {
  border-color: var(--brand-primary);
  background: #f8fafc;
  box-shadow: 0 0 0 1px var(--brand-primary);
}

.stats-card__top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.stats-bar__label {
  display: inline-flex;
  align-items: center;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-secondary);
}

.stats-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 10px;
}

.stats-icon svg {
  width: 20px;
  height: 20px;
}

.stats-icon--blue { background: rgba(59, 130, 246, 0.1); color: #3b82f6; }
.stats-icon--green { background: rgba(16, 185, 129, 0.1); color: #10b981; }
.stats-icon--amber { background: rgba(245, 158, 11, 0.1); color: #f59e0b; }
.stats-icon--purple { background: rgba(139, 92, 246, 0.1); color: #8b5cf6; }

.stats-card__bottom {
  display: flex;
  align-items: baseline;
}

.stats-bar__num {
  font-family: 'Poppins', 'JetBrains Mono', sans-serif;
  font-size: 2.1rem;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1;
}

.stats-bar__dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--el-color-warning);
  margin-left: 8px;
  animation: pulse-ring 2s infinite;
}

@keyframes pulse-ring {
  0% { box-shadow: 0 0 0 0 rgba(245, 158, 11, 0.4); }
  70% { box-shadow: 0 0 0 6px rgba(245, 158, 11, 0); }
  100% { box-shadow: 0 0 0 0 rgba(245, 158, 11, 0); }
}

@media (max-width: 900px) {
  .stats-bar {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
