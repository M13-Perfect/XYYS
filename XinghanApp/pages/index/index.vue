<template>
  <view class="home-container">
    <global-nav activeMenu="首页" />

    <view class="hero-wrap">
      <swiper class="banner-swiper" circular autoplay indicator-dots>
        <swiper-item v-for="item in bannerList" :key="item.id">
          <view class="banner-item" @click="goDetail(item.id)">
            <image class="banner-img" :src="item.posterUrl || '/static/default-poster.png'" mode="aspectFill" />
            <view class="banner-mask"></view>
            <view class="banner-content">
              <text class="banner-title">{{ item.title || '精选佳片' }}</text>
              <text class="banner-meta">{{ getVideoMeta(item) }}</text>
              <text class="banner-copy">{{ item.recommendText || item.description || '今晚值得打开的一部作品' }}</text>
              <view class="banner-button" @click.stop="goDetail(item.id)">查看详情</view>
            </view>
          </view>
        </swiper-item>
      </swiper>
    </view>

    <view class="mini-tabs">
      <view class="tab-item" v-for="nav in quickNavs" :key="nav.id" @click="goCategory(nav.name)">
        <text class="tab-text">{{ nav.name }}</text>
      </view>
    </view>

    <view class="video-section">
      <view class="section-heading"><text class="section-title">正在热播</text><text class="section-subtitle">Now Trending</text></view>
      <view class="list-wrapper">
        <view class="video-card" v-for="video in videoList" :key="video.id" @click="goDetail(video.id)">
          <view class="cover-wrap">
            <image class="cover" :src="video.posterUrl || '/static/default-poster.png'" mode="aspectFill" />
            <view class="badge">{{ video.updateStatus || '更新中' }}</view>
          </view>
          <text class="title">{{ video.title }}</text>
          <text class="desc">{{ video.subCategory || video.region || '精彩推荐' }}</text>
        </view>
      </view>
      <view class="load-status">
        <text v-if="loading">加载中...</text>
        <text v-else-if="noMore">已经到底啦</text>
      </view>
    </view>

    <view class="go-top" v-if="showTop" @click="scrollToTop">↑</view>
  </view>
</template>

<script setup>
import { ref } from 'vue';
import { onLoad, onReachBottom, onPullDownRefresh, onPageScroll } from '@dcloudio/uni-app';
import GlobalNav from '@/components/global-nav/global-nav.vue';
import request from '@/utils/request.js';

const bannerList = ref([]);
const videoList = ref([]);
const quickNavs = ref([
  { id: 1, name: '电影' },
  { id: 2, name: '剧集' },
  { id: 3, name: '动漫' },
  { id: 4, name: '综艺' }
]);

const pageNum = ref(1);
const pageSize = 12;
const loading = ref(false);
const noMore = ref(false);
const showTop = ref(false);

const fetchHomeData = async (reset = false) => {
  if (loading.value || (noMore.value && !reset)) return;
  if (reset) {
    pageNum.value = 1;
    noMore.value = false;
    videoList.value = [];
  }
  loading.value = true;
  try {
    if (reset) {
      const banners = await request({ url: '/app/video/banner' });
      bannerList.value = banners || [];
    }
    const pageData = await request({
      url: '/app/video/list',
      data: { sort: '最热', page: pageNum.value, size: pageSize }
    });
    const records = pageData?.records || [];
    videoList.value = reset ? records : [...videoList.value, ...records];
    if (records.length < pageSize) noMore.value = true;
    pageNum.value += 1;
  } finally {
    loading.value = false;
    uni.stopPullDownRefresh();
  }
};

onLoad(() => fetchHomeData(true));
onPullDownRefresh(() => fetchHomeData(true));
onReachBottom(() => fetchHomeData(false));

onPageScroll((e) => {
  showTop.value = e.scrollTop > 500;
});

const scrollToTop = () => {
  uni.pageScrollTo({ scrollTop: 0, duration: 300 });
};

const getVideoMeta = (video = {}) => {
  const meta = [video.subCategory, video.year, video.region].filter(Boolean);
  return meta.length ? meta.join(' · ') : '精选 · 热播 · 推荐';
};

const goDetail = (id) => uni.navigateTo({ url: `/pages/video/detail?id=${id}` });
const goCategory = (typeName) => uni.navigateTo({ url: `/pages/video/category?type=${encodeURIComponent(typeName)}` });
</script>

<style scoped>
.home-container { min-height: 100vh; background: #111111; color: #fff; padding-top: 96rpx; }
.hero-wrap { padding: 18rpx 22rpx 0; }
.banner-swiper { height: 410rpx; border-radius: 24rpx; overflow: hidden; background: #181818; }
.banner-item { position: relative; width: 100%; height: 100%; overflow: hidden; }
.banner-img { width: 100%; height: 100%; }
.banner-mask { position: absolute; left: 0; right: 0; bottom: 0; height: 76%; background: linear-gradient(180deg, rgba(17, 17, 17, 0) 0%, rgba(17, 17, 17, 0.52) 42%, rgba(17, 17, 17, 0.94) 100%); }
.banner-content { position: absolute; left: 28rpx; right: 28rpx; bottom: 28rpx; display: flex; flex-direction: column; align-items: flex-start; }
.banner-title { max-width: 82%; color: #fff; font-size: 40rpx; font-weight: 700; line-height: 1.18; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.banner-meta { color: rgba(255, 255, 255, 0.76); font-size: 22rpx; margin-top: 10rpx; }
.banner-copy { max-width: 86%; color: rgba(255, 255, 255, 0.64); font-size: 22rpx; line-height: 1.45; margin-top: 8rpx; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.banner-button { margin-top: 18rpx; padding: 10rpx 28rpx; border-radius: 999rpx; background: #d6c08a; color: #151515; font-size: 22rpx; font-weight: 600; box-shadow: 0 8rpx 22rpx rgba(0, 0, 0, 0.28); }

.mini-tabs { display: flex; align-items: center; gap: 14rpx; padding: 24rpx 22rpx 30rpx; overflow-x: auto; }
.tab-item { flex: 0 0 auto; min-width: 112rpx; padding: 12rpx 26rpx; border-radius: 999rpx; background: rgba(255, 255, 255, 0.06); border: 1rpx solid rgba(255, 255, 255, 0.10); text-align: center; }
.tab-text { color: rgba(255, 255, 255, 0.82); font-size: 24rpx; }

.video-section { padding: 0 22rpx 28rpx; }
.section-heading { display: flex; align-items: baseline; gap: 12rpx; margin-bottom: 18rpx; }
.section-title { color: #f4f4f4; font-size: 34rpx; font-weight: 700; letter-spacing: 1rpx; }
.section-subtitle { color: rgba(255, 255, 255, 0.36); font-size: 20rpx; letter-spacing: 1rpx; }
.list-wrapper { display: grid; grid-template-columns: repeat(3, 1fr); gap: 18rpx; }
.video-card { background: rgba(255, 255, 255, 0.035); border-radius: 18rpx; overflow: hidden; border: 1rpx solid rgba(255, 255, 255, 0.06); }
.cover-wrap { position: relative; overflow: hidden; border-radius: 18rpx; }
.cover { width: 100%; height: 196rpx; border-radius: 18rpx; display: block; }
.badge { position: absolute; right: 8rpx; bottom: 8rpx; max-width: calc(100% - 24rpx); background: rgba(0, 0, 0, 0.62); color: rgba(255, 255, 255, 0.92); font-size: 18rpx; padding: 4rpx 10rpx; border-radius: 999rpx; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.title { display: block; color: #fff; font-size: 23rpx; padding: 12rpx 8rpx 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.desc { display: block; color: rgba(255, 255, 255, 0.46); font-size: 20rpx; padding: 6rpx 8rpx 12rpx; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.load-status { text-align: center; color: rgba(255, 255, 255, 0.36); font-size: 22rpx; padding: 24rpx 0; }

.go-top { position: fixed; right: 24rpx; bottom: 120rpx; width: 64rpx; height: 64rpx; background: rgba(255, 255, 255, 0.14); color: #fff; border: 1rpx solid rgba(255, 255, 255, 0.12); border-radius: 50%; text-align: center; line-height: 64rpx; font-size: 34rpx; z-index: 1000; box-shadow: 0 6rpx 20rpx rgba(0, 0, 0, 0.35); }
</style>
