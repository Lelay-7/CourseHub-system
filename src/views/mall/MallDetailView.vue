<template>
  <AppLayout>
    <div class="page-content">
      <PageHeader title="商品详情" :show-back="true" />

      <div v-if="loading" class="detail-loading">
        <LoadingSkeleton type="list" :rows="5" />
      </div>

      <template v-else-if="goods.goods_id">
        <transition name="success-pop">
          <div v-if="exchangeSuccess" class="success-banner card">
            <el-icon><CircleCheckFilled /></el-icon>
            <div>
              <strong>兑换成功</strong>
              <span>已生成兑换记录，可在订单中查看进度。</span>
            </div>
          </div>
        </transition>

        <div class="detail-card card">
          <!-- 商品图片轮播 -->
          <el-carousel v-if="goods.images?.length > 1" height="360px" class="goods-carousel">
            <el-carousel-item v-for="(img, i) in goods.images" :key="i">
              <img :src="img" class="img-cover" />
            </el-carousel-item>
          </el-carousel>
          <img
            v-else
            :src="goods.cover_url || defaultImg"
            class="goods-cover-img"
          />

          <!-- 商品信息 -->
          <div class="goods-info">
            <h2>{{ goods.name }}</h2>
            <div class="goods-meta">
              <span class="goods-score">
                <el-icon><StarFilled /></el-icon>
                <strong>{{ goods.exchange_score }}</strong> 积分
              </span>
              <el-tag :type="stockTag">{{ stockText }}</el-tag>
            </div>
            <div class="goods-desc">
              <h4>兑换说明</h4>
              <p>{{ goods.description || '暂无说明' }}</p>
            </div>
          </div>
        </div>

        <!-- 兑换按钮 -->
        <div class="exchange-bar card">
          <div class="flex-between">
            <div class="balance-info">
              <span>我的积分：</span>
              <strong :class="{ insufficient: userStore.scoreBalance < goods.exchange_score }">
                {{ userStore.scoreBalance }}
              </strong>
              <em v-if="scoreDelta">-{{ scoreDelta }}</em>
            </div>
            <el-button
              type="primary"
              size="large"
              :disabled="!canExchange"
              :loading="exchanging"
              @click="handleExchange"
            >
              {{ exchangeButtonText }}
            </el-button>
          </div>
        </div>

        <!-- 兑换记录 -->
        <div class="exchange-records card" v-if="exchangeRecords.length > 0">
          <h3>我的兑换记录</h3>
          <el-timeline>
            <el-timeline-item
              v-for="record in exchangeRecords"
              :key="record.exchange_id"
              :timestamp="formatTime(record.exchange_time)"
            >
              {{ EXCHANGE_STATUS_LABEL[record.status] || '未知' }}
            </el-timeline-item>
          </el-timeline>
        </div>
      </template>

      <ErrorState v-else title="商品不存在" @retry="fetchDetail" />
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { StarFilled, CircleCheckFilled } from '@element-plus/icons-vue'
import AppLayout from '@/components/AppLayout.vue'
import PageHeader from '@/components/PageHeader.vue'
import LoadingSkeleton from '@/components/LoadingSkeleton.vue'
import ErrorState from '@/components/ErrorState.vue'
import { useUserStore } from '@/stores/user'
import { EXCHANGE_STATUS_LABEL } from '@/utils/constants'
import * as mallApi from '@/api/mall'

const route = useRoute()
const userStore = useUserStore()

const goods = ref({})
const exchangeRecords = ref([])
const loading = ref(true)
const exchanging = ref(false)
const exchangeSuccess = ref(false)
const scoreDelta = ref(0)

const defaultImg = new URL('@/assets/images/default-cover.svg', import.meta.url).href

const stockTag = computed(() => {
  if (goods.value.stock <= 0) return 'danger'
  if (goods.value.stock < 10) return 'warning'
  return 'success'
})
const stockText = computed(() => {
  if (goods.value.stock <= 0) return '已售罄'
  return `库存 ${goods.value.stock}`
})

const canExchange = computed(() =>
  goods.value.stock > 0 &&
  userStore.scoreBalance >= (goods.value.exchange_score || 0) &&
  !goods.value.has_exchanged
)

const exchangeButtonText = computed(() => {
  if (goods.value.stock <= 0) return '已售罄'
  if (goods.value.has_exchanged) return '已兑换'
  if (userStore.scoreBalance < (goods.value.exchange_score || 0)) return '积分不足'
  return '立即兑换'
})

async function fetchDetail() {
  loading.value = true
  try {
    const [detailRes, recordsRes] = await Promise.all([
      mallApi.getGoodsDetail(route.params.id),
      mallApi.getExchangeList({ goods_id: route.params.id })
    ])
    if (detailRes.code === 200) goods.value = detailRes.data || {}
    if (recordsRes.code === 200) {
      exchangeRecords.value = (recordsRes.data?.list || recordsRes.data?.records || [])
    }
  } catch { /* handled */ }
  finally { loading.value = false }
}

async function handleExchange() {
  if (!canExchange.value) {
    if (userStore.scoreBalance < (goods.value.exchange_score || 0)) {
      ElMessageBox.alert(
        '积分不足！您可以通过以下方式赚取积分：\n1. 每日签到 +10分\n2. 学习课程 +5分/课时\n3. 评价课程 +3分\n4. 分享内容 +2分',
        '积分不足',
        { confirmButtonText: '知道了', type: 'warning' }
      )
    }
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认使用 ${goods.value.exchange_score} 积分兑换「${goods.value.name}」吗？`,
      '兑换确认',
      { confirmButtonText: '确认兑换', cancelButtonText: '取消', type: 'warning' }
    )
  } catch { return } // 用户取消

  exchanging.value = true
  try {
    const before = userStore.scoreBalance
    const res = await mallApi.exchangeGoods({ goods_id: goods.value.goods_id })
    if (res.code === 200) {
      const after = res.data?.score_balance ?? userStore.scoreBalance - goods.value.exchange_score
      scoreDelta.value = Math.max(0, before - after)
      userStore.updateScoreBalance(after)
      goods.value.has_exchanged = true
      goods.value.stock = Math.max(0, (goods.value.stock || 0) - 1)
      exchangeRecords.value.unshift({
        exchange_id: Date.now(),
        status: 1,
        exchange_time: new Date().toISOString()
      })
      exchangeSuccess.value = true
      ElMessage.success('兑换成功，积分已更新')
      setTimeout(() => { scoreDelta.value = 0 }, 1800)
    }
  } catch { /* handled */ }
  finally { exchanging.value = false }
}

function formatTime(time) {
  if (!time) return ''
  return new Date(time).toLocaleDateString('zh-CN')
}

onMounted(fetchDetail)
</script>

<style scoped>
.detail-loading { padding: var(--spacing-lg); }
.detail-card { margin-bottom: var(--spacing-md); overflow: hidden; padding: 0; }

.success-banner {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-md);
  color: var(--color-primary-dark);
  background: linear-gradient(135deg, var(--color-bg-soft), #fffdf8);
}

.success-banner .el-icon {
  color: var(--color-success);
  font-size: 30px;
}

.success-banner div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.success-banner span {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.goods-carousel { }
.goods-cover-img {
  width: 100%; max-height: 360px; object-fit: cover; display: block;
}

.goods-info { padding: var(--spacing-lg); }
.goods-info h2 { margin: 0 0 var(--spacing-md); }
.goods-meta {
  display: flex; align-items: center; gap: var(--spacing-md); margin-bottom: var(--spacing-lg);
}
.goods-score {
  display: flex; align-items: center; gap: 4px;
  color: var(--color-warning); font-size: var(--font-size-lg);
}
.goods-score strong { font-size: var(--font-size-xxl); }

.goods-desc h4 { margin: 0 0 var(--spacing-sm); color: var(--color-text-regular); }
.goods-desc p { color: var(--color-text-secondary); line-height: 1.8; }

.exchange-bar {
  margin-bottom: var(--spacing-md); padding: var(--spacing-md);
}
.balance-info strong { font-size: var(--font-size-xl); color: var(--color-primary); }
.balance-info strong.insufficient { color: var(--color-danger); }
.balance-info em {
  margin-left: var(--spacing-sm);
  color: var(--color-danger);
  font-style: normal;
  font-weight: 800;
  animation: float-up 1.6s ease both;
}

.exchange-records { padding: var(--spacing-lg); }
.exchange-records h3 { margin: 0 0 var(--spacing-md); }

.success-pop-enter-active,
.success-pop-leave-active {
  transition: opacity var(--transition-normal), transform var(--transition-normal);
}
.success-pop-enter-from,
.success-pop-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

@keyframes float-up {
  0% { opacity: 0; transform: translateY(8px); }
  20% { opacity: 1; transform: translateY(0); }
  100% { opacity: 0; transform: translateY(-12px); }
}
</style>
