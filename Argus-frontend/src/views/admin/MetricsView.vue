<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import {
  fetchMetricsOverview,
  fetchUserRank,
  fetchGroupRank,
  type Period,
  type MetricsOverview,
  type UsageRankItem,
} from '@/api/metrics'
import { extractApiError } from '@/api/http'

// ── 时间段选项 ──
const periodOptions: { label: string; value: Period }[] = [
  { label: '今天', value: 'TODAY' },
  { label: '7天', value: 'LAST_7_DAYS' },
  { label: '14天', value: 'LAST_14_DAYS' },
  { label: '30天', value: 'LAST_30_DAYS' },
]

const selectedPeriod = ref<Period>('LAST_7_DAYS')

// ── 数据状态 ──
const overview = ref<MetricsOverview | null>(null)
const userRank = ref<UsageRankItem[]>([])
const groupRank = ref<UsageRankItem[]>([])

const loadingOverview = ref(false)
const loadingRank = ref(false)
const errorMsg = ref('')

// ── 格式化工具 ──
function formatNumber(n: number): string {
  return n.toLocaleString('zh-CN')
}

function formatCost(n: number): string {
  return n.toFixed(4)
}

function formatPercent(n: number): string {
  return n.toFixed(1) + '%'
}

function formatDate(dateStr: string): string {
  const parts = dateStr.split('-')
  if (parts.length === 3) return `${parts[1]}-${parts[2]}`
  return dateStr
}

// ── 用户头像颜色 ──
const AVATAR_COLORS = [
  'linear-gradient(135deg, #f59e0b, #ea580c)',
  'linear-gradient(135deg, #64748b, #475569)',
  'linear-gradient(135deg, #f97316, #ea580c)',
  'linear-gradient(135deg, #3b82f6, #2563eb)',
  'linear-gradient(135deg, #8b5cf6, #6d28d9)',
  'linear-gradient(135deg, #14b8a6, #0d9488)',
  'linear-gradient(135deg, #ec4899, #db2777)',
  'linear-gradient(135deg, #06b6d4, #0891b2)',
  'linear-gradient(135deg, #84cc16, #65a30d)',
  'linear-gradient(135deg, #f43f5e, #e11d48)',
]

function getAvatarColor(index: number): string {
  return (AVATAR_COLORS[index % AVATAR_COLORS.length] as string) ?? AVATAR_COLORS[0]!
}

function getInitial(name: string): string {
  return name.charAt(0).toUpperCase()
}

// ── 排行徽章类型 ──
function getRankBadgeClass(idx: number): string {
  if (idx === 0) return 'rank-badge--gold'
  if (idx === 1) return 'rank-badge--silver'
  if (idx === 2) return 'rank-badge--bronze'
  return ''
}

// ── SVG 趋势图数据 ──
const CHART_WIDTH = 900
const CHART_HEIGHT = 220
const CHART_PAD_LEFT = 8
const CHART_PAD_RIGHT = 8

const trendChart = computed(() => {
  const trend = overview.value?.dailyTrend ?? []
  if (trend.length === 0) return null

  const innerW = CHART_WIDTH - CHART_PAD_LEFT - CHART_PAD_RIGHT
  const maxVal = Math.max(
    ...trend.map((t) => t.requests),
    ...trend.map((t) => t.tokens),
    1,
  )

  // 生成折线点
  const toX = (i: number) => CHART_PAD_LEFT + (i / Math.max(trend.length - 1, 1)) * innerW
  const toY = (v: number) => CHART_HEIGHT - (v / maxVal) * CHART_HEIGHT

  const requestPoints = trend.map((t, i) => `${toX(i)},${toY(t.requests)}`).join(' ')
  const tokenPoints = trend.map((t, i) => `${toX(i)},${toY(t.tokens)}`).join(' ')

  const lastItem = trend[trend.length - 1]
  if (!lastItem) return null
  const lastX = toX(trend.length - 1)

  // 面积填充路径
  const requestArea = `M${toX(0)},${CHART_HEIGHT} L${requestPoints} L${lastX},${CHART_HEIGHT} Z`
  const tokenArea = `M${toX(0)},${CHART_HEIGHT} L${tokenPoints} L${lastX},${CHART_HEIGHT} Z`

  // Y 轴刻度
  const yTicks = [0, 0.25, 0.5, 0.75, 1].map((f) => Math.round(maxVal * f))

  // X 轴标签（稀疏采样）
  const xLabels: { x: number; label: string }[] = []
  const maxLabels = 8
  const step = Math.max(1, Math.floor(trend.length / maxLabels))
  for (let i = 0; i < trend.length; i += step) {
    xLabels.push({ x: toX(i), label: formatDate(trend[i]!.date) })
  }
  // 确保最后一个日期在
  if (xLabels.length > 0 && xLabels[xLabels.length - 1]!.x < lastX - 20) {
    xLabels.push({ x: lastX, label: formatDate(lastItem!.date) })
  }

  return {
    requestPoints,
    tokenPoints,
    requestArea,
    tokenArea,
    yTicks,
    xLabels,
    lastX,
    lastYRequest: toY(lastItem.requests),
    lastYToken: toY(lastItem.tokens),
    gridYs: [0, 0.25, 0.5, 0.75, 1].map((f) => CHART_HEIGHT - f * CHART_HEIGHT),
  }
})

// ── 数据加载 ──
async function loadOverview() {
  loadingOverview.value = true
  errorMsg.value = ''
  try {
    overview.value = await fetchMetricsOverview()
  } catch (err) {
    errorMsg.value = extractApiError(err, '加载概览数据失败')
  } finally {
    loadingOverview.value = false
  }
}

async function loadRanks() {
  loadingRank.value = true
  try {
    const [users, groups] = await Promise.all([
      fetchUserRank(selectedPeriod.value, 10),
      fetchGroupRank(selectedPeriod.value, 10),
    ])
    userRank.value = users
    groupRank.value = groups
  } catch (err) {
    errorMsg.value = extractApiError(err, '加载排行数据失败')
  } finally {
    loadingRank.value = false
  }
}

watch(selectedPeriod, () => {
  loadRanks()
})

onMounted(() => {
  loadOverview()
  loadRanks()
})
</script>

<template>
  <div class="metrics-page">
    <!-- 页头 -->
    <div class="page-header">
      <h1>使用统计</h1>
      <p>LLM 调用量、Token 消耗与费用分析</p>
    </div>

    <!-- 错误提示 -->
    <div v-if="errorMsg" class="error-banner">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
        <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="1.5" />
        <path d="M12 8V12" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
        <circle cx="12" cy="16" r="0.5" fill="currentColor" stroke="none" />
      </svg>
      <span>{{ errorMsg }}</span>
    </div>

    <!-- ── KPI 卡片 ── -->
    <div v-if="loadingOverview" class="loading-placeholder">
      <div class="skeleton-card" v-for="i in 4" :key="i" />
    </div>
    <div v-else-if="overview" class="kpi-grid">
      <div class="kpi-card kpi-card--blue">
        <div class="kpi-card__icon">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
            <path d="M13 2L3 14H12L11 22L21 10H12L13 2Z" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" />
          </svg>
        </div>
        <div class="kpi-card__label">今日调用次数</div>
        <div class="kpi-card__value">{{ formatNumber(overview.todayRequests) }}</div>
      </div>
      <div class="kpi-card kpi-card--teal">
        <div class="kpi-card__icon">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
            <path d="M4 19.5A2.5 2.5 0 016.5 17H20" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
            <path d="M6.5 2H20V22H6.5A2.5 2.5 0 014 19.5V4.5A2.5 2.5 0 016.5 2Z" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" />
          </svg>
        </div>
        <div class="kpi-card__label">今日 Token 消耗</div>
        <div class="kpi-card__value">{{ formatNumber(overview.todayTokens) }}</div>
      </div>
      <div class="kpi-card kpi-card--amber">
        <div class="kpi-card__icon">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
            <path d="M12 1V23M17 5H9.5C7.567 5 6 6.567 6 8.5C6 10.433 7.567 12 9.5 12H14.5C16.433 12 18 13.567 18 15.5C18 17.433 16.433 19 14.5 19H6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </div>
        <div class="kpi-card__label">今日费用（元）</div>
        <div class="kpi-card__value">{{ formatCost(overview.todayCost) }}</div>
      </div>
      <div class="kpi-card kpi-card--green">
        <div class="kpi-card__icon">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
            <path d="M22 11.08V12C21.9988 14.1564 21.3005 16.2547 20.0093 17.9818C18.7182 19.709 16.9033 20.9725 14.8354 21.5839C12.7674 22.1953 10.5573 22.1219 8.53447 21.3746C6.51168 20.6273 4.78465 19.2461 3.61096 17.4371C2.43727 15.628 1.87979 13.4881 2.02168 11.3363C2.16356 9.18457 2.99721 7.13633 4.39828 5.49707C5.79935 3.85782 7.69279 2.71538 9.79619 2.24015C11.8996 1.76491 14.1003 1.98234 16.07 2.86" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
            <path d="M22 4L12 14.01L9 11.01" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </div>
        <div class="kpi-card__label">今日成功率</div>
        <div class="kpi-card__value">{{ formatPercent(overview.todaySuccessRate) }}</div>
      </div>
    </div>
    <div v-else class="empty-state">
      <p>暂无概览数据</p>
    </div>

    <!-- ── 趋势图（SVG 面积折线图） ── -->
    <div class="section-card">
      <div class="section-card__header">
        <h2 class="section-card__title">调用趋势</h2>
        <span class="section-card__hint">近30天概览数据</span>
      </div>

      <div v-if="trendChart" class="trend-chart-area">
        <!-- 图例 -->
        <div class="trend-legend">
          <span class="trend-legend__item">
            <span class="trend-legend__dot trend-legend__dot--blue" />
            调用量
          </span>
          <span class="trend-legend__item">
            <span class="trend-legend__dot trend-legend__dot--teal" />
            Token 消耗
          </span>
        </div>

        <div class="trend-chart-body">
          <!-- Y 轴 -->
          <div class="trend-y-axis">
            <span v-for="tick in trendChart.yTicks.slice().reverse()" :key="tick">
              {{ tick >= 10000 ? (tick / 1000).toFixed(0) + 'k' : tick.toLocaleString() }}
            </span>
          </div>

          <!-- SVG 画布 -->
          <div class="trend-svg-wrap">
            <svg viewBox="0 0 900 220" preserveAspectRatio="none" class="trend-svg">
              <defs>
                <linearGradient id="trendGradBlue" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stop-color="#3b82f6" stop-opacity="0.25" />
                  <stop offset="100%" stop-color="#3b82f6" stop-opacity="0.02" />
                </linearGradient>
                <linearGradient id="trendGradTeal" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stop-color="#14b8a6" stop-opacity="0.25" />
                  <stop offset="100%" stop-color="#14b8a6" stop-opacity="0.02" />
                </linearGradient>
              </defs>
              <!-- 网格线 -->
              <line
                v-for="(gy, gi) in trendChart.gridYs"
                :key="'g' + gi"
                :x1="0" :y1="gy" :x2="900" :y2="gy"
                stroke="#f1f5f9" stroke-width="1"
              />
              <!-- 面积 -->
              <path :d="trendChart.requestArea" fill="url(#trendGradBlue)" />
              <path :d="trendChart.tokenArea" fill="url(#trendGradTeal)" />
              <!-- 折线 -->
              <polyline
                :points="trendChart.requestPoints"
                fill="none" stroke="#3b82f6" stroke-width="2"
                stroke-linecap="round" stroke-linejoin="round"
              />
              <polyline
                :points="trendChart.tokenPoints"
                fill="none" stroke="#14b8a6" stroke-width="2"
                stroke-linecap="round" stroke-linejoin="round"
              />
              <!-- 末端圆点 -->
              <circle
                :cx="trendChart.lastX" :cy="trendChart.lastYRequest" r="4"
                fill="#fff" stroke="#3b82f6" stroke-width="2.5"
              />
              <circle
                :cx="trendChart.lastX" :cy="trendChart.lastYToken" r="4"
                fill="#fff" stroke="#14b8a6" stroke-width="2.5"
              />
            </svg>
            <!-- X 轴标签 -->
            <div class="trend-x-labels">
              <span
                v-for="(xl, xi) in trendChart.xLabels"
                :key="xi"
                :style="{ left: (xl.x / 900) * 100 + '%' }"
              >{{ xl.label }}</span>
            </div>
          </div>
        </div>
      </div>
      <div v-else-if="!loadingOverview" class="empty-state">
        <p>暂无趋势数据</p>
      </div>
    </div>

    <!-- ── 排行区域 ── -->
    <div class="rank-grid">
      <!-- 用户排行 -->
      <div class="section-card">
        <div class="section-card__header">
          <h2 class="section-card__title">用户排行</h2>
        </div>

        <div class="period-tabs">
          <button
            v-for="opt in periodOptions"
            :key="opt.value"
            class="period-tab"
            :class="{ active: selectedPeriod === opt.value }"
            @click="selectedPeriod = opt.value"
          >
            {{ opt.label }}
          </button>
        </div>

        <div v-if="loadingRank" class="loading-inline">
          <span>加载中...</span>
        </div>
        <table v-else-if="userRank.length > 0" class="rank-table">
          <thead>
            <tr>
              <th class="rank-num-col">#</th>
              <th>用户</th>
              <th class="num-col">调用次数</th>
              <th class="num-col">Token</th>
              <th class="num-col">费用</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, idx) in userRank" :key="item.id">
              <td class="rank-num-col">
                <span class="rank-badge" :class="getRankBadgeClass(idx)">{{ idx + 1 }}</span>
              </td>
              <td class="name-col">
                <div class="user-chip">
                  <span class="user-chip__avatar" :style="{ background: getAvatarColor(idx) }">
                    {{ getInitial(item.name) }}
                  </span>
                  <span class="user-chip__name">{{ item.name }}</span>
                </div>
              </td>
              <td class="num-col">{{ formatNumber(item.totalRequests) }}</td>
              <td class="num-col">{{ formatNumber(item.totalTokens) }}</td>
              <td class="num-col num-col--cost">{{ formatCost(item.totalCost) }}</td>
            </tr>
          </tbody>
        </table>
        <div v-else class="empty-state">
          <p>暂无用户排行数据</p>
        </div>
      </div>

      <!-- 群组排行 -->
      <div class="section-card">
        <div class="section-card__header">
          <h2 class="section-card__title">群组排行</h2>
        </div>

        <div class="period-tabs">
          <button
            v-for="opt in periodOptions"
            :key="opt.value"
            class="period-tab"
            :class="{ active: selectedPeriod === opt.value }"
            @click="selectedPeriod = opt.value"
          >
            {{ opt.label }}
          </button>
        </div>

        <div v-if="loadingRank" class="loading-inline">
          <span>加载中...</span>
        </div>
        <table v-else-if="groupRank.length > 0" class="rank-table">
          <thead>
            <tr>
              <th class="rank-num-col">#</th>
              <th>群组</th>
              <th class="num-col">调用次数</th>
              <th class="num-col">Token</th>
              <th class="num-col">费用</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, idx) in groupRank" :key="item.id">
              <td class="rank-num-col">
                <span class="rank-badge" :class="getRankBadgeClass(idx)">{{ idx + 1 }}</span>
              </td>
              <td class="name-col">{{ item.name }}</td>
              <td class="num-col">{{ formatNumber(item.totalRequests) }}</td>
              <td class="num-col">{{ formatNumber(item.totalTokens) }}</td>
              <td class="num-col num-col--cost">{{ formatCost(item.totalCost) }}</td>
            </tr>
          </tbody>
        </table>
        <div v-else class="empty-state">
          <p>暂无群组排行数据</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ── 页头 ── */
.page-header {
  margin-bottom: 32px;
}
.page-header h1 {
  font-size: 28px;
  font-weight: 800;
  color: var(--text-primary);
  letter-spacing: -0.02em;
  margin-bottom: 4px;
}
.page-header p {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0;
}

/* ── 错误提示 ── */
.error-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 18px;
  background: linear-gradient(135deg, rgba(239, 68, 68, 0.08), rgba(239, 68, 68, 0.03));
  border: 1px solid rgba(239, 68, 68, 0.2);
  border-radius: var(--radius-sm);
  color: #dc2626;
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 24px;
}
.error-banner svg {
  flex-shrink: 0;
  color: #ef4444;
}

/* ── KPI 卡片网格 ── */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 28px;
}

.kpi-card {
  position: relative;
  background: var(--surface-white);
  border-radius: 16px;
  padding: 24px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  cursor: default;
}
.kpi-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.06);
}

/* 渐变顶部装饰条 */
.kpi-card::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  border-radius: 3px;
}
.kpi-card--blue::after {
  background: linear-gradient(90deg, #3b82f6, #60a5fa);
}
.kpi-card--teal::after {
  background: linear-gradient(90deg, #14b8a6, #5eead4);
}
.kpi-card--amber::after {
  background: linear-gradient(90deg, #f59e0b, #fbbf24);
}
.kpi-card--green::after {
  background: linear-gradient(90deg, #22c55e, #4ade80);
}

.kpi-card__icon {
  width: 42px;
  height: 42px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}
.kpi-card--blue .kpi-card__icon {
  background: #eff6ff;
  color: #3b82f6;
}
.kpi-card--teal .kpi-card__icon {
  background: #f0fdfa;
  color: #14b8a6;
}
.kpi-card--amber .kpi-card__icon {
  background: #fffbeb;
  color: #f59e0b;
}
.kpi-card--green .kpi-card__icon {
  background: #f0fdf4;
  color: #22c55e;
}

.kpi-card__label {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-muted);
}
.kpi-card__value {
  font-family: 'Poppins', 'Noto Sans SC', sans-serif;
  font-size: 30px;
  font-weight: 800;
  color: var(--text-primary);
  letter-spacing: -0.02em;
  margin-top: 4px;
}

/* ── 加载骨架 ── */
.loading-placeholder {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 28px;
}
.skeleton-card {
  height: 140px;
  background: linear-gradient(
    90deg,
    var(--surface-muted) 25%,
    var(--surface-subtle) 50%,
    var(--surface-muted) 75%
  );
  background-size: 200% 100%;
  animation: shimmer 1.5s ease-in-out infinite;
  border-radius: 16px;
}

@keyframes shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}

/* ── 通用区域卡片 ── */
.section-card {
  background: var(--surface-white);
  border-radius: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  padding: 28px 32px;
  margin-bottom: 24px;
}
.section-card__header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 24px;
}
.section-card__title {
  font-size: 17px;
  font-weight: 700;
  color: var(--text-primary);
}
.section-card__hint {
  font-size: 13px;
  color: var(--text-muted);
}

/* ── 趋势图表（SVG 面积折线图） ── */
.trend-chart-area {
  display: flex;
  flex-direction: column;
}

.trend-legend {
  display: flex;
  gap: 24px;
  margin-bottom: 16px;
}
.trend-legend__item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-secondary);
}
.trend-legend__dot {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  flex-shrink: 0;
}
.trend-legend__dot--blue {
  background: #3b82f6;
}
.trend-legend__dot--teal {
  background: #14b8a6;
}

.trend-chart-body {
  display: flex;
  gap: 12px;
}

.trend-y-axis {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding-bottom: 22px;
  min-width: 40px;
  text-align: right;
  font-size: 11px;
  color: var(--text-muted);
}

.trend-svg-wrap {
  flex: 1;
  position: relative;
}
.trend-svg {
  width: 100%;
  height: 220px;
  display: block;
}

.trend-x-labels {
  display: flex;
  justify-content: space-between;
  position: relative;
  margin-top: 6px;
  font-size: 10px;
  color: var(--text-muted);
  padding: 0 2px;
}
.trend-x-labels span {
  position: absolute;
  transform: translateX(-50%);
  white-space: nowrap;
}

/* ── 时间段选择器（分段胶囊） ── */
.period-tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 20px;
  background: #f1f5f9;
  border-radius: 10px;
  padding: 4px;
  width: fit-content;
}
.period-tab {
  padding: 7px 16px;
  font-size: 13px;
  font-weight: 500;
  border-radius: 8px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.15s ease;
  border: none;
  background: none;
  font-family: inherit;
}
.period-tab:hover {
  color: var(--text-primary);
}
.period-tab.active {
  background: #fff;
  color: #3b82f6;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

/* ── 排行区域 ── */
.rank-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}

/* ── 排行表格 ── */
.rank-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.rank-table th {
  text-align: left;
  padding: 12px 14px;
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.06em;
  border-bottom: 2px solid #e2e8f0;
  white-space: nowrap;
}
.rank-table td {
  padding: 14px;
  border-bottom: 1px solid #f1f5f9;
  color: var(--text-primary);
}
.rank-table tbody tr {
  transition: background 0.15s;
}
.rank-table tbody tr:hover {
  background: #f8fafc;
}
.rank-table tbody tr:last-child td {
  border-bottom: none;
}

.rank-num-col {
  width: 44px;
  text-align: center;
}
.num-col {
  text-align: right;
  font-family: 'Poppins', 'Noto Sans SC', sans-serif;
  font-weight: 500;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.num-col--cost {
  color: var(--text-primary);
}
.name-col {
  font-weight: 500;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ── 排名徽章 ── */
.rank-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-muted);
  background: #f1f5f9;
}
.rank-badge--gold {
  background: linear-gradient(135deg, #fbbf24, #f59e0b);
  color: #fff;
}
.rank-badge--silver {
  background: linear-gradient(135deg, #94a3b8, #64748b);
  color: #fff;
}
.rank-badge--bronze {
  background: linear-gradient(135deg, #f97316, #ea580c);
  color: #fff;
}

/* ── 用户芯片 ── */
.user-chip {
  display: flex;
  align-items: center;
  gap: 10px;
}
.user-chip__avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  flex-shrink: 0;
}
.user-chip__name {
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ── 空状态 ── */
.empty-state {
  text-align: center;
  padding: 40px 20px;
  color: var(--text-muted);
  font-size: 14px;
}

/* ── 加载中 ── */
.loading-inline {
  text-align: center;
  padding: 40px 20px;
  color: var(--text-muted);
  font-size: 14px;
}

/* ── 响应式 ── */
@media (max-width: 1024px) {
  .kpi-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .loading-placeholder {
    grid-template-columns: repeat(2, 1fr);
  }
  .rank-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .kpi-grid {
    grid-template-columns: 1fr;
  }
  .loading-placeholder {
    grid-template-columns: 1fr;
  }
  .kpi-card__value {
    font-size: 24px;
  }
  .trend-svg {
    height: 160px;
  }
  .section-card {
    padding: 20px 18px;
  }
}
</style>
