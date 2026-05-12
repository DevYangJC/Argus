<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import LoginModal from '@/components/LoginModal.vue'

const router = useRouter()
const authStore = useAuthStore()

const showLoginModal = ref(false)

function navigateToApp() {
  if (authStore.isAuthenticated) {
    router.push(authStore.homePath)
  } else {
    showLoginModal.value = true
  }
}

// scroll reveal
onMounted(() => {
  nextTick(() => {
    const reveals = document.querySelectorAll('.scroll-reveal')
    const obs = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('is-visible')
            obs.unobserve(entry.target)
          }
        })
      },
      { threshold: 0.15 }
    )
    reveals.forEach((el) => obs.observe(el))
  })
})

const features = [
  {
    icon: `<svg width="22" height="22" viewBox="0 0 24 24" fill="none"><path d="M12 2L2 7L12 12L22 7L12 2Z" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/><path d="M2 17L12 22L22 17" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>`,
    title: '文档知识管理',
    desc: '上传多种格式文档，自动切片、向量化、索引入库，支持断点续传。',
  },
  {
    icon: `<svg width="22" height="22" viewBox="0 0 24 24" fill="none"><circle cx="11" cy="11" r="7" stroke="currentColor" stroke-width="1.5"/><path d="M19 19L16 16" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>`,
    title: '知识库问答',
    desc: '群组内提问，混合检索（关键词 + 语义向量）驱动 LLM 生成可溯源回答。',
  },
  {
    icon: `<svg width="22" height="22" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.5"/><path d="M8.5 12C8.5 10.067 10.067 8.5 12 8.5C13.933 8.5 15.5 10.067 15.5 12C15.5 13.933 13.933 15.5 12 15.5C10.067 15.5 8.5 13.933 8.5 12Z" stroke="currentColor" stroke-width="1.5"/></svg>`,
    title: 'AI 智能助手',
    desc: '多轮对话 Agent，支持 SSE 流式输出、工具编排与短期记忆管理。',
  },
  {
    icon: `<svg width="22" height="22" viewBox="0 0 24 24" fill="none"><circle cx="9" cy="6" r="3" stroke="currentColor" stroke-width="1.5"/><circle cx="15" cy="6" r="3" stroke="currentColor" stroke-width="1.5"/><path d="M3 18V17C3 14.7909 4.79086 13 7 13H11C13.2091 13 15 14.7909 15 17V18" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/><path d="M15 13H17C19.2091 13 21 14.7909 21 17V18" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>`,
    title: '团队协作',
    desc: '创建知识库群组，邀请成员，审批申请，群组间数据隔离。',
  },
  {
    icon: `<svg width="22" height="22" viewBox="0 0 24 24" fill="none"><path d="M8 3H5C3.89543 3 3 3.89543 3 5V8M21 8V5C21 3.89543 20.1046 3 19 3H16M3 16V19C3 20.1046 3.89543 21 5 21H8M16 21H19C20.1046 21 21 20.1046 21 19V16" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>`,
    title: '答案溯源',
    desc: '每条回答附带引用片段与相关度评分，让 AI 回答有据可查。',
  },
  {
    icon: `<svg width="22" height="22" viewBox="0 0 24 24" fill="none"><rect x="3" y="3" width="18" height="18" rx="4" stroke="currentColor" stroke-width="1.5"/><path d="M12 7V12L15 14" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>`,
    title: '安全保障',
    desc: '三级权限体系，JWT + BCrypt 认证，全链路数据加密。',
  },
]

const steps = [
  { num: '01', title: '创建群组，组建团队', desc: '创建知识库群组，通过邀请码或审批机制邀请成员加入协作。' },
  { num: '02', title: '上传文档，自动入库', desc: '上传文档至群组知识库，系统自动完成切片、向量化与索引。' },
  { num: '03', title: '提问检索，获取答案', desc: '在知识库内提问，混合检索驱动 LLM 生成带引用的可信回答。' },
  { num: '04', title: 'AI 助手，持续对话', desc: 'Agent 自主编排工具调用，流式输出，短期记忆管理。' },
]

const cases = [
  { icon: 'power', title: '电力', desc: '巡检手册、安全规程、故障排除问答' },
  { icon: 'finance', title: '金融', desc: '合规检索、风控政策、产品知识库' },
  { icon: 'education', title: '教育', desc: '课程知识库、学员自助答疑' },
  { icon: 'manufacture', title: '制造', desc: '技术文档、设备SOP、供应商资料' },
]
</script>

<template>
  <div class="landing">
    <!-- ════════ Login Modal ════════ -->
    <LoginModal v-model:visible="showLoginModal" />

    <!-- ════════ Navbar ════════ -->
    <header class="navbar">
      <div class="nav-inner container">
        <a href="/" class="nav-brand">
          <svg width="28" height="28" viewBox="0 0 32 32" fill="none">
            <defs>
              <linearGradient id="navGrad" x1="2" y1="2" x2="30" y2="30"><stop stop-color="#4A90D9"/><stop offset="1" stop-color="#5CC9C1"/></linearGradient>
              <path id="navPetal" d="M16 3C17.5 6 20 9 22.5 10C20 11 17.5 11.5 16 13C14.5 11.5 12 11 9.5 10C12 9 14.5 6 16 3Z" fill="url(#navGrad)" opacity="0.7"/>
            </defs>
            <use href="#navPetal"/>
            <use href="#navPetal" transform="rotate(90 16 16)"/>
            <use href="#navPetal" transform="rotate(180 16 16)"/>
            <use href="#navPetal" transform="rotate(270 16 16)"/>
            <circle cx="16" cy="16" r="5.5" fill="url(#navGrad)"/>
          </svg>
          <span>数据洞察中心</span>
        </a>
        <nav class="nav-links">
          <a href="#features">核心功能</a>
          <a href="#workflow">工作流程</a>
          <a href="#cases">应用场景</a>
        </nav>
        <div class="nav-actions">
          <button class="btn-ghost" @click="showLoginModal = true">登录</button>
          <button class="btn-nav-cta" @click="showLoginModal = true">免费试用</button>
        </div>
      </div>
    </header>

    <!-- ════════ Hero ════════ -->
    <section class="hero">
      <div class="hero-bg">
        <div class="hero-grid"></div>
        <div class="hero-glow hero-glow-1"></div>
        <div class="hero-glow hero-glow-2"></div>
      </div>
      <div class="hero-content container">
        <div class="hero-badge">
          <span class="badge-pulse"></span>
          RAG + Agent · 企业级智能知识平台
        </div>
        <h1 class="hero-title">
          让每一次提问<br>
          <span class="text-gradient">都有据可查</span>
        </h1>
        <p class="hero-desc">
          基于检索增强生成与 AI Agent 技术，将企业私有知识库与大语言模型深度融合，
          实现精准溯源、自主编排的可信智能知识服务。
        </p>
        <div class="hero-actions">
          <button class="btn-primary" @click="showLoginModal = true">
            开始使用
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none"><path d="M5 12H19M19 12L13 6M19 12L13 18" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>
          </button>
          <button class="btn-outline" @click="navigateToApp()">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none"><path d="M8 6L14 12L8 18" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>
            进入工作台
          </button>
        </div>

        <!-- Metrics -->
        <div class="hero-metrics">
          <div class="metric">
            <span class="metric-value">98<span class="metric-unit">%</span></span>
            <span class="metric-label">检索准确率</span>
          </div>
          <div class="metric-divider"></div>
          <div class="metric">
            <span class="metric-value">&lt;200<span class="metric-unit">ms</span></span>
            <span class="metric-label">平均响应</span>
          </div>
          <div class="metric-divider"></div>
          <div class="metric">
            <span class="metric-value">20<span class="metric-unit">+</span></span>
            <span class="metric-label">文件格式支持</span>
          </div>
        </div>
      </div>
    </section>

    <!-- ════════ Features ════════ -->
    <section class="features" id="features">
      <div class="container">
        <div class="section-header scroll-reveal">
          <span class="section-tag">核心功能</span>
          <h2 class="section-title">覆盖知识管理全链路</h2>
          <p class="section-desc">从文档入库到 AI 对话，一站式构建企业智能知识服务体系</p>
        </div>

        <div class="features-grid">
          <div v-for="(f, i) in features" :key="i" class="feature-card scroll-reveal" :style="{ transitionDelay: `${i * 0.06}s` }">
            <div class="feature-icon" v-html="f.icon"></div>
            <h3>{{ f.title }}</h3>
            <p>{{ f.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ════════ Workflow ════════ -->
    <section class="workflow" id="workflow">
      <div class="container container-narrow">
        <div class="section-header scroll-reveal">
          <span class="section-tag">工作流程</span>
          <h2 class="section-title">四步开启智能知识服务</h2>
          <p class="section-desc">从零到一，快速构建企业级 RAG 应用</p>
        </div>

        <div class="steps-list">
          <div v-for="(s, i) in steps" :key="s.num" class="step-row scroll-reveal" :style="{ transitionDelay: `${i * 0.08}s` }">
            <div class="step-num">{{ s.num }}</div>
            <div class="step-divider-v"></div>
            <div class="step-card">
              <h3>{{ s.title }}</h3>
              <p>{{ s.desc }}</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ════════ Cases ════════ -->
    <section class="cases" id="cases">
      <div class="container">
        <div class="section-header scroll-reveal">
          <span class="section-tag">应用场景</span>
          <h2 class="section-title">赋能各行各业</h2>
          <p class="section-desc">灵活适配不同业务场景，释放企业知识资产价值</p>
        </div>

        <div class="cases-grid">
          <div v-for="(c, i) in cases" :key="i" class="case-card scroll-reveal" :style="{ transitionDelay: `${i * 0.06}s` }">
            <div class="case-icon" v-html="caseIcons[c.icon]"></div>
            <h3>{{ c.title }}</h3>
            <p>{{ c.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ════════ CTA ════════ -->
    <section class="cta">
      <div class="container">
        <div class="cta-card scroll-reveal">
          <div class="cta-bg-orb cta-orb-1"></div>
          <div class="cta-bg-orb cta-orb-2"></div>
          <h2>准备好构建您的<br>智能知识平台了吗？</h2>
          <p>立即体验企业级 RAG + Agent 解决方案，让每一次提问都有据可查。</p>
          <div class="cta-buttons">
            <button class="btn-cta-primary" @click="showLoginModal = true">免费试用</button>
            <button class="btn-cta-outline" @click="navigateToApp()">查看演示</button>
          </div>
        </div>
      </div>
    </section>

    <!-- ════════ Footer ════════ -->
    <footer class="footer">
      <div class="container">
        <div class="footer-grid">
          <div class="footer-brand">
            <div class="footer-logo-row">
              <svg width="24" height="24" viewBox="0 0 32 32" fill="none">
                <defs>
                  <linearGradient id="ftGrad" x1="2" y1="2" x2="30" y2="30"><stop stop-color="#4A90D9"/><stop offset="1" stop-color="#5CC9C1"/></linearGradient>
                  <path id="ftPetal" d="M16 3C17.5 6 20 9 22.5 10C20 11 17.5 11.5 16 13C14.5 11.5 12 11 9.5 10C12 9 14.5 6 16 3Z" fill="url(#ftGrad)" opacity="0.7"/>
                </defs>
                <use href="#ftPetal"/>
                <use href="#ftPetal" transform="rotate(90 16 16)"/>
                <use href="#ftPetal" transform="rotate(180 16 16)"/>
                <use href="#ftPetal" transform="rotate(270 16 16)"/>
                <circle cx="16" cy="16" r="5.5" fill="url(#ftGrad)"/>
              </svg>
              <span>数据洞察中心</span>
            </div>
            <p>融合 RAG 与 AI Agent 技术的企业级智能知识平台</p>
          </div>
          <div class="footer-col">
            <h4>产品</h4>
            <a href="#features">核心功能</a>
            <a href="#workflow">工作流程</a>
            <a href="#cases">应用场景</a>
          </div>
          <div class="footer-col">
            <h4>接口</h4>
            <a href="#">REST API</a>
            <a href="#">SSE 流式</a>
            <a href="#">认证鉴权</a>
          </div>
          <div class="footer-col">
            <h4>关于</h4>
            <a href="#">项目文档</a>
            <a href="#">架构设计</a>
            <a href="#">更新日志</a>
          </div>
        </div>
        <div class="footer-bottom">
          <p>&copy; 2026 数据洞察中心. All rights reserved.</p>
        </div>
      </div>
    </footer>
  </div>
</template>

<script lang="ts">
// SVG icons for case cards (registered separately to avoid reactive overhead)
const caseIcons: Record<string, string> = {
  power: `<svg width="28" height="28" viewBox="0 0 24 24" fill="none"><path d="M13 2L4 14H11L11 22L20 10H13L13 2Z" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/></svg>`,
  finance: `<svg width="28" height="28" viewBox="0 0 24 24" fill="none"><rect x="2" y="4" width="20" height="16" rx="3" stroke="currentColor" stroke-width="1.5"/><path d="M12 9V15M9 12H15" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>`,
  education: `<svg width="28" height="28" viewBox="0 0 24 24" fill="none"><path d="M4 7L12 3L20 7L12 11L4 7Z" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/><path d="M6 10V16C6 16 8 19 12 19C16 19 18 16 18 16V10" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>`,
  manufacture: `<svg width="28" height="28" viewBox="0 0 24 24" fill="none"><rect x="2" y="2" width="20" height="20" rx="3" stroke="currentColor" stroke-width="1.5"/><path d="M8 8H16M8 12H16M8 16H12" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>`,
}
</script>

<style scoped>
/* ════════════ Navbar ════════════ */
.navbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-bottom: 1px solid var(--border-default);
}

.nav-inner {
  height: 66px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.nav-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  font-family: 'Poppins', 'Noto Sans SC', sans-serif;
  font-weight: 700;
  font-size: 17px;
  color: var(--text-primary);
  letter-spacing: -0.01em;
}

.nav-links {
  display: flex;
  gap: 36px;
}

.nav-links a {
  font-size: 13.5px;
  font-weight: 500;
  color: var(--text-secondary);
  transition: color 0.2s ease;
}

.nav-links a:hover {
  color: var(--brand-primary);
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn-ghost {
  padding: 7px 18px;
  border-radius: var(--radius-xs);
  font-size: 13.5px;
  font-weight: 500;
  color: var(--text-secondary);
  background: transparent;
  transition: all 0.2s ease;
}

.btn-ghost:hover {
  color: var(--brand-primary);
  background: rgba(74, 144, 217, 0.06);
}

.btn-nav-cta {
  padding: 7px 20px;
  border-radius: var(--radius-xs);
  font-size: 13.5px;
  font-weight: 600;
  color: #fff;
  background: var(--brand-primary);
  transition: all 0.2s ease;
}

.btn-nav-cta:hover {
  background: var(--brand-primary-dark);
}

/* ════════════ Hero ════════════ */
.hero {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  background: linear-gradient(180deg, #f0f6fc 0%, #f8fafc 60%, #ffffff 100%);
  overflow: hidden;
  padding: 100px 0 80px;
}

.hero-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.hero-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(74, 144, 217, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(74, 144, 217, 0.04) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: radial-gradient(ellipse 70% 70% at 50% 40%, black 30%, transparent 70%);
}

.hero-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(120px);
}

.hero-glow-1 {
  width: 500px;
  height: 500px;
  background: rgba(74, 144, 217, 0.12);
  top: -100px;
  right: -150px;
}

.hero-glow-2 {
  width: 350px;
  height: 350px;
  background: rgba(92, 201, 193, 0.1);
  bottom: -80px;
  left: -100px;
}

.hero-content {
  text-align: center;
  position: relative;
  z-index: 2;
  max-width: 720px;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 5px 16px;
  border-radius: 100px;
  background: rgba(74, 144, 217, 0.08);
  border: 1px solid rgba(74, 144, 217, 0.15);
  font-size: 13px;
  font-weight: 500;
  color: var(--brand-primary-dark);
  margin-bottom: 32px;
  animation: fade-in-up 0.5s ease;
}

.badge-pulse {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--brand-accent);
  animation: pulse-ring 2.5s ease-in-out infinite;
}

.hero-title {
  font-family: 'Poppins', 'Noto Sans SC', sans-serif;
  font-size: clamp(42px, 6vw, 68px);
  font-weight: 800;
  line-height: 1.12;
  letter-spacing: -0.03em;
  color: var(--text-primary);
  margin-bottom: 20px;
  animation: fade-in-up 0.5s ease 0.1s both;
}

.hero-desc {
  font-size: 16px;
  line-height: 1.7;
  color: var(--text-secondary);
  max-width: 560px;
  margin: 0 auto 40px;
  animation: fade-in-up 0.5s ease 0.2s both;
}

.hero-actions {
  display: flex;
  gap: 14px;
  justify-content: center;
  animation: fade-in-up 0.5s ease 0.3s both;
}

.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 14px 32px;
  border-radius: var(--radius-sm);
  background: var(--brand-primary);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  transition: all 0.2s ease;
  box-shadow: 0 4px 16px rgba(74, 144, 217, 0.3);
  letter-spacing: 0.01em;
}

.btn-primary:hover {
  background: var(--brand-primary-light);
  box-shadow: 0 6px 24px rgba(74, 144, 217, 0.4);
  transform: translateY(-1px);
}

.btn-outline {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 14px 32px;
  border-radius: var(--radius-sm);
  border: 1.5px solid var(--border-default);
  color: var(--text-secondary);
  font-size: 15px;
  font-weight: 600;
  background: transparent;
  transition: all 0.2s ease;
  letter-spacing: 0.01em;
}

.btn-outline:hover {
  border-color: var(--brand-primary);
  color: var(--brand-primary);
  background: rgba(74, 144, 217, 0.04);
}

/* Hero metrics */
.hero-metrics {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 40px;
  margin-top: 80px;
  animation: fade-in-up 0.5s ease 0.4s both;
}

.metric {
  text-align: center;
}

.metric-value {
  display: block;
  font-family: 'Poppins', sans-serif;
  font-size: 32px;
  font-weight: 800;
  color: var(--text-primary);
  line-height: 1;
  margin-bottom: 6px;
}

.metric-unit {
  font-size: 18px;
  color: var(--brand-accent-dark);
}

.metric-label {
  font-size: 12.5px;
  color: var(--text-muted);
  font-weight: 500;
  letter-spacing: 0.02em;
}

.metric-divider {
  width: 1px;
  height: 36px;
  background: var(--border-default);
}

/* ════════════ Section Headers ════════════ */
.section-header {
  text-align: center;
  margin-bottom: 64px;
}

.section-tag {
  display: inline-block;
  padding: 4px 14px;
  border-radius: 100px;
  background: rgba(74, 144, 217, 0.08);
  color: var(--brand-primary-dark);
  font-size: 12.5px;
  font-weight: 600;
  letter-spacing: 0.03em;
  margin-bottom: 16px;
}

.section-title {
  font-family: 'Poppins', 'Noto Sans SC', sans-serif;
  font-size: clamp(26px, 3.5vw, 38px);
  font-weight: 800;
  color: var(--text-primary);
  letter-spacing: -0.02em;
  margin-bottom: 12px;
}

.section-desc {
  font-size: 15px;
  color: var(--text-secondary);
  max-width: 440px;
  margin: 0 auto;
}

/* ════════════ Features ════════════ */
.features {
  padding: 120px 0;
  background: var(--surface-white);
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.feature-card {
  padding: 36px 30px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-subtle);
  background: var(--surface-white);
  transition: all var(--ease-in-out);
  cursor: pointer;
}

.feature-card:hover {
  border-color: rgba(74, 144, 217, 0.15);
  box-shadow: var(--shadow-lg);
  transform: translateY(-2px);
}

.feature-icon {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  background: var(--surface-accent);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--brand-primary);
  margin-bottom: 20px;
  transition: all var(--ease-in-out);
}

.feature-card:hover .feature-icon {
  background: var(--brand-primary);
  color: #fff;
}

.feature-card h3 {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.feature-card p {
  font-size: 13.5px;
  color: var(--text-secondary);
  line-height: 1.65;
  margin: 0;
}

/* ════════════ Workflow ════════════ */
.workflow {
  padding: 120px 0;
  background: var(--surface-subtle);
}

.steps-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.step-row {
  display: flex;
  gap: 28px;
  align-items: stretch;
}

.step-num {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(74, 144, 217, 0.1), rgba(92, 201, 193, 0.08));
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: 'Poppins', sans-serif;
  font-size: 20px;
  font-weight: 800;
  color: var(--brand-primary);
  flex-shrink: 0;
}

.step-divider-v {
  width: 2px;
  background: linear-gradient(to bottom, var(--brand-primary), var(--brand-accent));
  border-radius: 1px;
  flex-shrink: 0;
  opacity: 0.2;
  margin: 4px 0;
}

.step-row:last-child .step-divider-v {
  opacity: 0;
}

.step-card {
  flex: 1;
  padding: 18px 0 36px;
}

.step-card h3 {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 6px;
}

.step-card p {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.6;
  margin: 0;
}

/* ════════════ Cases ════════════ */
.cases {
  padding: 120px 0;
  background: var(--surface-white);
}

.cases-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.case-card {
  padding: 32px 20px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-subtle);
  background: var(--surface-white);
  text-align: center;
  transition: all var(--ease-in-out);
  cursor: pointer;
}

.case-card:hover {
  border-color: rgba(74, 144, 217, 0.15);
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.case-icon {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  background: var(--surface-accent);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--brand-primary);
  margin: 0 auto 14px;
  transition: all var(--ease-in-out);
}

.case-card:hover .case-icon {
  background: var(--brand-primary);
  color: #fff;
}

.case-card h3 {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 6px;
}

.case-card p {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
  margin: 0;
}

/* ════════════ CTA ════════════ */
.cta {
  padding: 100px 0;
  background: var(--surface-subtle);
}

.cta-card {
  position: relative;
  text-align: center;
  padding: 80px 48px;
  border-radius: var(--radius-xl);
  background: linear-gradient(135deg, var(--brand-primary), var(--brand-primary-dark), #2a6cb6);
  overflow: hidden;
}

.cta-bg-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  pointer-events: none;
}

.cta-orb-1 {
  width: 280px;
  height: 280px;
  background: rgba(92, 201, 193, 0.2);
  top: -100px;
  right: -60px;
}

.cta-orb-2 {
  width: 200px;
  height: 200px;
  background: rgba(255, 255, 255, 0.1);
  bottom: -80px;
  left: -40px;
}

.cta-card h2 {
  font-family: 'Poppins', 'Noto Sans SC', sans-serif;
  font-size: clamp(26px, 3.5vw, 38px);
  font-weight: 800;
  color: #fff;
  position: relative;
  z-index: 1;
  margin-bottom: 16px;
  letter-spacing: -0.02em;
}

.cta-card p {
  font-size: 15px;
  color: rgba(255, 255, 255, 0.75);
  position: relative;
  z-index: 1;
  margin-bottom: 36px;
}

.cta-buttons {
  display: flex;
  gap: 14px;
  justify-content: center;
  position: relative;
  z-index: 1;
}

.btn-cta-primary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 14px 32px;
  border-radius: var(--radius-sm);
  background: #fff;
  color: var(--brand-primary-dark);
  font-size: 15px;
  font-weight: 600;
  transition: all 0.2s ease;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
}
.btn-cta-primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.18);
}

.btn-cta-outline {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 14px 32px;
  border-radius: var(--radius-sm);
  border: 1.5px solid rgba(255, 255, 255, 0.4);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  background: transparent;
  transition: all 0.2s ease;
}
.btn-cta-outline:hover {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.7);
}

/* ════════════ Footer ════════════ */
.footer {
  background: var(--surface-footer);
  padding: 72px 0 32px;
}

.footer-grid {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr 1fr;
  gap: 48px;
  margin-bottom: 48px;
}

.footer-logo-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-family: 'Poppins', 'Noto Sans SC', sans-serif;
  font-weight: 700;
  font-size: 16px;
  color: var(--text-on-dark);
  margin-bottom: 10px;
}

.footer-brand p {
  font-size: 13.5px;
  color: var(--text-on-dark-muted);
  line-height: 1.7;
  margin: 0;
}

.footer-col h4 {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-on-dark-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 16px;
}

.footer-col a {
  display: block;
  font-size: 13.5px;
  color: var(--text-on-dark-muted);
  padding: 3px 0;
  transition: color 0.2s ease;
}

.footer-col a:hover {
  color: var(--text-on-dark);
}

.footer-bottom {
  padding-top: 28px;
  border-top: 1px solid var(--border-on-dark);
  text-align: center;
}

.footer-bottom p {
  font-size: 13px;
  color: var(--text-on-dark-muted);
  margin: 0;
}

/* ════════════ Responsive ════════════ */
@media (max-width: 1024px) {
  .features-grid { grid-template-columns: repeat(2, 1fr); }
  .cases-grid { grid-template-columns: repeat(2, 1fr); }
  .footer-grid { grid-template-columns: 1fr 1fr; }
}

@media (max-width: 768px) {
  .nav-links { display: none; }
  .hero { padding: 140px 0 60px; }
  .hero-title { font-size: 36px; }
  .hero-actions { flex-direction: column; align-items: center; }
  .hero-metrics { gap: 24px; }
  .metric-value { font-size: 26px; }
  .features-grid { grid-template-columns: 1fr; }
  .cases-grid { grid-template-columns: 1fr 1fr; }
  .cta-card { padding: 48px 24px; }
  .cta-buttons { flex-direction: column; align-items: center; }
  .footer-grid { grid-template-columns: 1fr; gap: 32px; }
}
</style>
