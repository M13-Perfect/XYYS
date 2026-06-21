<template>
  <view class="detail-container" v-if="video">
    <global-nav :isTransparent="true" />

    <view class="backdrop-wrapper">
      <image class="backdrop-img" :src="video.posterUrl || '/static/default-poster.png'" mode="aspectFill"></image>
      <view class="backdrop-mask"></view>
    </view>

    <view class="info-section">
      <text class="title">{{ video.title || '加载中...' }}</text>
      <view class="tags-row">
        <text class="tag-text">{{ video.publishYear || '未知年份' }}</text>
        <text class="dot">·</text>
        <text class="tag-text">{{ video.region || '未知地区' }}</text>
        <text class="dot">·</text>
        <text class="tag-text">{{ video.subCategory || '视频' }}</text>
        <text class="dot">·</text>
        <text class="tag-text">共 {{ episodeList.length }} 集</text>
      </view>

      <view class="meta-info">
        <text class="intro-title">剧情简介</text>
        <text class="meta-line truncate-4">{{ ext.description || '暂无简介' }}</text>
      </view>

      <view class="action-row">
        <view class="play-btn" @click="handlePlay(currentEp)">
          <text class="icon">▶</text>
          <text>开始观看</text>
        </view>
      </view>
    </view>

    <view class="player-section">
      <scroll-view scroll-x class="source-scroll" :show-scrollbar="false">
        <view class="source-list">
          <view class="source-item" :class="{ active: currentSource === index }" v-for="(source, index) in sources" :key="index" @click="currentSource = index">
            <text class="source-name">{{ source.name }}</text>
          </view>
        </view>
      </scroll-view>

      <view class="episode-grid">
        <view class="ep-item" :class="{ active: currentEp === ep.index }" v-for="ep in episodeList" :key="ep.index" @click="selectEpisode(ep.index)">
          {{ ep.name }}
        </view>
      </view>
    </view>

    <view class="recommend-section">
      <view class="section-title">相关推荐</view>
      <view class="recommend-grid">
        <view class="rec-card" v-for="rec in recommends" :key="rec.id" @click="goDetail(rec.id)">
          <image class="rec-poster" :src="rec.posterUrl || '/static/default-poster.png'" mode="aspectFill"></image>
          <text class="rec-title">{{ rec.title }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import GlobalNav from '@/components/global-nav/global-nav.vue';
import request from '@/utils/request.js';

const video = ref(null);
const ext = ref({});
const sources = ref([{ name: '默认源' }]);
const episodeList = ref([{ index: 1, name: '01' }]);
const currentSource = ref(0);
const currentEp = ref(1);
const recommends = ref([]);

onLoad(async (options) => {
  if (!options.id) return;
  await fetchVideoDetail(options.id);
  await fetchRecommend(options.id, video.value?.categoryId);
});

const parseEpisodes = (playUrlRaw, fallbackCount) => {
  if (!playUrlRaw) {
    const count = Number(fallbackCount) || 1;
    return Array.from({ length: count }, (_, i) => ({ index: i + 1, name: (i + 1).toString().padStart(2, '0') }));
  }

  const parts = String(playUrlRaw)
    .split('#')
    .map((s) => s.trim())
    .filter(Boolean);

  if (!parts.length) {
    return [{ index: 1, name: '01' }];
  }

  return parts.map((item, idx) => {
    const [name] = item.split('$');
    return {
      index: idx + 1,
      name: (name || `${idx + 1}`).slice(0, 8)
    };
  });
};

const fetchVideoDetail = async (id) => {
  try {
    const res = await request({ url: `/app/video/detail/${id}`, method: 'GET' });
    if (!res) return;
    video.value = res;

    let extInfo = {};
    try {
      extInfo = res.extInfo ? JSON.parse(res.extInfo) : {};
    } catch (e) {}
    ext.value = extInfo;

    episodeList.value = parseEpisodes(res.playUrl, res.episodeCount);
    currentEp.value = 1;
  } catch (e) {
    console.error('获取详情失败', e);
  }
};

const fetchRecommend = async (currentVideoId, categoryId) => {
  try {
    const data = await request({
      url: '/app/video/recommend',
      data: { currentVideoId, categoryId }
    });
    recommends.value = Array.isArray(data) ? data : [];
  } catch (e) {
    recommends.value = [];
  }
};

const handlePlay = (ep) => {
  const token = uni.getStorageSync('token');
  if (!token) {
    uni.showToast({ title: '请先登录后观看', icon: 'none' });
    setTimeout(() => uni.navigateTo({ url: '/pages/login/login' }), 800);
    return;
  }

  uni.navigateTo({
    url: `/pages/video/play?id=${video.value.id}&ep=${ep}&source=${currentSource.value}`
  });
};

const selectEpisode = (ep) => {
  currentEp.value = ep;
  handlePlay(ep);
};

const goDetail = (id) => {
  uni.redirectTo({ url: `/pages/video/detail?id=${id}` });
};
</script>

<style scoped>
.detail-container { min-height: 100vh; background-color: #0b0f19; color: #fff; padding-bottom: 60rpx; position: relative; overflow-x: hidden; }
.backdrop-wrapper { position: absolute; top: 0; left: 0; width: 100vw; height: 820rpx; z-index: 0; overflow: hidden; }
.backdrop-img { width: 100%; height: 100%; opacity: 0.34; display: block; filter: blur(10rpx) saturate(0.9); transform: scale(1.05); }
.backdrop-mask { position: absolute; inset: 0; background: linear-gradient(to bottom, rgba(11,15,25,0.96) 0%, rgba(11,15,25,0.28) 22%, rgba(11,15,25,0.45) 58%, #0b0f19 100%), linear-gradient(to right, rgba(11,15,25,0.72) 0%, rgba(11,15,25,0.08) 30%, rgba(11,15,25,0.08) 70%, rgba(11,15,25,0.72) 100%), radial-gradient(ellipse at center, rgba(11,15,25,0) 35%, rgba(11,15,25,0.42) 100%); }
.info-section { position: relative; z-index: 10; padding: 80rpx 40rpx 36rpx; margin-top: 100rpx; }
.title { font-size: 52rpx; font-weight: 700; line-height: 1.18; margin-bottom: 20rpx; display: block; letter-spacing: 1rpx; }
.tags-row { display: flex; align-items: center; flex-wrap: wrap; margin-bottom: 34rpx; font-size: 24rpx; color: #aeb7c8; }
.tag-text { color: #aeb7c8; line-height: 1.6; }
.dot { color: #68748a; margin: 0 12rpx; }
.meta-info { margin-top: 6rpx; }
.intro-title { display: block; color: #edf1f8; font-size: 30rpx; font-weight: 700; margin-bottom: 12rpx; }
.meta-line { color: #a5afc0; font-size: 26rpx; line-height: 1.85; display: block; }
.truncate-4 { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 4; overflow: hidden; }
.action-row { display: flex; align-items: center; margin-top: 30rpx; }
.play-btn { background: linear-gradient(135deg, #fff7de 0%, #f2d28a 100%); color: #14100a; font-size: 29rpx; font-weight: 700; padding: 18rpx 46rpx; border-radius: 999rpx; display: flex; align-items: center; box-shadow: 0 12rpx 30rpx rgba(242, 210, 138, 0.22); }
.play-btn .icon { margin-right: 12rpx; font-size: 24rpx; }
.player-section { position: relative; z-index: 10; padding: 0 40rpx; margin-top: 20rpx; }
.source-scroll { white-space: nowrap; margin-bottom: 28rpx; }
.source-list { display: inline-flex; padding-bottom: 4rpx; }
.source-item { margin-right: 18rpx; color: #8a95a8; font-size: 25rpx; padding: 12rpx 22rpx; border-radius: 999rpx; background: rgba(255,255,255,0.05); border: 1rpx solid rgba(255,255,255,0.08); }
.source-item.active { color: #f3d58e; background: rgba(243,213,142,0.10); border-color: rgba(243,213,142,0.34); font-weight: 600; }
.episode-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: 14rpx; }
.ep-item { background-color: #171d2a; border: 1rpx solid #242c3d; color: #c4cad6; text-align: center; padding: 16rpx 0; font-size: 24rpx; border-radius: 10rpx; }
.ep-item.active { background-color: #f4f0e6; border-color: #f4f0e6; color: #12151c; font-weight: 700; }
.recommend-section { padding: 42rpx 18rpx 0; position: relative; z-index: 10; }
.section-title { font-size: 34rpx; font-weight: 700; margin-bottom: 16rpx; }
.recommend-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14rpx; }
.rec-card { background: #141a27; border-radius: 10rpx; overflow: hidden; border: 1rpx solid #20293a; }
.rec-poster { width: 100%; height: 190rpx; display: block; }
.rec-title { display: block; color: #e9edf5; font-size: 22rpx; padding: 10rpx; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
</style>
