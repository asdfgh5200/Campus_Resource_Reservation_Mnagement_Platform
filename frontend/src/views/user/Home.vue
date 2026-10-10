<template>
  <div class="home-page">
    <!-- 顶部横幅 -->
    <div class="hero-banner">
      <div class="hero-overlay"></div>
      <div class="hero-content">
        <h1>校园资源预约平台</h1>
        <p>教室 · 实验室 · 图书馆 · 体育馆，一站式预约管理</p>
      </div>
    </div>

    <!-- 分类卡片 -->
    <div class="category-grid">
      <div
        v-for="cat in categories"
        :key="cat.type"
        class="category-card"
        :style="{ backgroundImage: `url(${cat.image})` }"
        @click="goCategory(cat.type)"
      >
        <div class="card-overlay" :style="{ background: cat.gradient }"></div>
        <div class="card-content">
          <el-icon :size="40" class="card-icon"><component :is="cat.icon" /></el-icon>
          <h2 class="card-title">{{ cat.label }}</h2>
          <p class="card-desc">{{ cat.desc }}</p>
          <div class="card-stat">
            <span class="stat-num">{{ cat.count }}</span>
            <span class="stat-label">个资源</span>
          </div>
          <div class="card-enter">点击进入 <el-icon><ArrowRight /></el-icon></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, School, Tools, Reading, Trophy } from '@element-plus/icons-vue'
import { mockApi } from '@/api/mock'

const router = useRouter()

const categories = ref([
  {
    type: 1, label: '教室', icon: 'School',
    desc: '教学楼A栋、B栋，共10间智慧教室',
    image: '/assets/type-classroom.png',
    gradient: 'linear-gradient(135deg, rgba(102,126,234,0.75) 0%, rgba(118,75,162,0.75) 100%)',
    count: 0
  },
  {
    type: 2, label: '实验室', icon: 'Tools',
    desc: '实验楼6层，物理/化学/生物/材料/计算机/机电',
    image: '/assets/type-lab.png',
    gradient: 'linear-gradient(135deg, rgba(67,233,123,0.75) 0%, rgba(56,249,215,0.75) 100%)',
    count: 0
  },
  {
    type: 3, label: '图书馆', icon: 'Reading',
    desc: '自习区、研讨室、阅览区',
    image: '/assets/type-library.png',
    gradient: 'linear-gradient(135deg, rgba(79,172,254,0.75) 0%, rgba(0,242,254,0.75) 100%)',
    count: 0
  },
  {
    type: 4, label: '体育馆', icon: 'Trophy',
    desc: 'A栋（游泳馆/乒乓球）+ B栋（篮球/羽毛球/网球/排球）',
    image: '/assets/type-gym.png',
    gradient: 'linear-gradient(135deg, rgba(240,147,251,0.75) 0%, rgba(245,87,108,0.75) 100%)',
    count: 0
  }
])

function goCategory(type) {
  router.push(`/category/${type}`)
}

onMounted(async () => {
  for (const cat of categories.value) {
    const res = await mockApi.getResourcePage({ current: 1, size: 1, type: cat.type })
    if (res.code === 200) cat.count = res.data.total
  }
})
</script>

<style scoped lang="scss">
.home-page { max-width: 1200px; margin: 0 auto; }

.hero-banner {
  height: 200px;
  border-radius: 16px;
  overflow: hidden;
  position: relative;
  margin-bottom: 28px;
  background-image: url('/assets/bg-main.jpg');
  background-size: cover;
  background-position: center;

  .hero-overlay {
    position: absolute; inset: 0;
    background: linear-gradient(135deg, rgba(102,126,234,0.6) 0%, rgba(118,75,162,0.6) 100%);
  }
  .hero-content {
    position: relative; z-index: 1;
    padding: 50px 40px;
    h1 { font-size: 28px; font-weight: 700; color: #fff; margin-bottom: 8px; text-shadow: 0 2px 10px rgba(0,0,0,0.2); }
    p { font-size: 15px; color: rgba(255,255,255,0.9); }
  }
}

.category-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
}

.category-card {
  height: 280px;
  border-radius: 16px;
  overflow: hidden;
  position: relative;
  cursor: pointer;
  transition: transform 0.3s, box-shadow 0.3s;
  background-size: cover;
  background-position: center;

  &:hover {
    transform: translateY(-6px);
    box-shadow: 0 16px 40px rgba(0,0,0,0.15);

    .card-enter { opacity: 1; transform: translateX(0); }
  }

  .card-overlay {
    position: absolute; inset: 0;
    transition: opacity 0.3s;
  }

  .card-content {
    position: relative; z-index: 1;
    padding: 32px 36px;
    height: 100%;
    display: flex;
    flex-direction: column;
    color: #fff;

    .card-icon { margin-bottom: 12px; }
    .card-title { font-size: 24px; font-weight: 700; margin-bottom: 8px; text-shadow: 0 2px 8px rgba(0,0,0,0.2); }
    .card-desc { font-size: 13px; color: rgba(255,255,255,0.9); margin-bottom: auto; line-height: 1.6; }
  }

  .card-stat {
    display: flex;
    align-items: baseline;
    gap: 4px;
    margin-bottom: 12px;

    .stat-num { font-size: 32px; font-weight: 800; }
    .stat-label { font-size: 14px; opacity: 0.85; }
  }

  .card-enter {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 14px;
    opacity: 0.7;
    transition: all 0.3s;
    transform: translateX(-8px);
  }
}
</style>
