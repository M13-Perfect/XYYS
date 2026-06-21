<template>
  <view class="play-container">
    <global-nav />

    <view class="video-wrapper">
      <video
        :key="playerKey"
        id="myVideo"
        class="video-player"
        :src="playUrl"
        :poster="posterUrl"
        :title="videoTitle"
        :autoplay="true"
        :controls="true"
        :show-progress="true"
        :show-fullscreen-btn="true"
        :enable-progress-gesture="true"
        :enable-play-gesture="true"
        :page-gesture="false"
        :http-cache="true"
        :initial-time="initialTime"
        :mobilenet-hint-type="0"
        object-fit="contain"
        @timeupdate="onTimeUpdate"
        @loadedmetadata="onLoadedMeta"
        @waiting="onWaiting"
        @play="onPlaying"
        @pause="onPaused"
        @ended="onPaused"
        @error="onVideoError"
      ></video>
    </view>

    <view class="quick-controls">
      <button class="ctrl" @click="seekBy(-10)">后退10s</button>
      <button class="ctrl" @click="togglePlay">{{ paused ? '播放' : '暂停' }}</button>
      <button class="ctrl" @click="seekBy(10)">快进10s</button>
      <text class="buffering" v-if="buffering">缓冲中...</text>
      <text class="time">{{ displayTime }} / {{ displayDuration }}</text>
    </view>

    <view class="control-panel">
      <text class="playing-title">{{ videoTitle }} - 第 {{ currentEp }} 集</text>

      <view class="section" v-if="isM3u8">
        <text class="m3u8-tag">当前播放格式：M3U8</text>
      </view>

      <view class="section">
        <text class="section-title">选集</text>
        <view class="episode-grid">
          <view class="ep-item" :class="{ active: currentEp === ep.index, disabled: loading }" v-for="ep in episodeList" :key="ep.index" @click="changeEpisode(ep.index)">
            {{ ep.name }}
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import GlobalNav from '@/components/global-nav/global-nav.vue';
import request from '@/utils/request.js';

const videoId = ref('');
const videoTitle = ref('视频播放');
const posterUrl = ref('/static/default-poster.png');
const playUrl = ref('');
const currentEp = ref(1);
const episodeList = ref([{ index: 1, name: '01' }]);
const isM3u8 = ref(false);

const videoCtx = ref(null);
const playerKey = ref(0);
const loading = ref(false);
const requestSeq = ref(0);
const playInfoCache = new Map();
const paused = ref(false);
const currentTime = ref(0);
const duration = ref(0);
const initialTime = ref(0);
const buffering = ref(false);
const lastUiUpdateAt = ref(0);

const formatTime = (sec) => {
  const s = Math.max(0, Math.floor(sec || 0));
  const m = Math.floor(s / 60).toString().padStart(2, '0');
  const ss = (s % 60).toString().padStart(2, '0');
  return `${m}:${ss}`;
};

const displayTime = computed(() => formatTime(currentTime.value));
const displayDuration = computed(() => formatTime(duration.value));

const initVideoCtx = () => {
  videoCtx.value = uni.createVideoContext('myVideo');
};

const onTimeUpdate = (e) => {
  const now = Date.now();
  const detail = e.detail || {};
  const nextDuration = Number(detail.duration || duration.value || 0);

  if (nextDuration && Math.abs(nextDuration - duration.value) > 0.5) {
    duration.value = nextDuration;
  }

  // video 的 timeupdate 在部分端会非常频繁，频繁触发 Vue 响应式更新会抢占解码线程。
  // 将 UI 时间刷新限制到约 4fps，播放核心仍交给原生 video 组件持续推进。
  if (now - lastUiUpdateAt.value < 250) {
    return;
  }

  currentTime.value = Number(detail.currentTime || 0);
  lastUiUpdateAt.value = now;
};

const onLoadedMeta = (e) => {
  duration.value = Number(e.detail?.duration || 0);
};

const onWaiting = () => {
  buffering.value = true;
};

const onPlaying = () => {
  paused.value = false;
  buffering.value = false;
};

const onPaused = () => {
  paused.value = true;
  buffering.value = false;
};

const onVideoError = (e) => {
  buffering.value = false;
  console.error('视频播放异常', e);
  uni.showToast({ title: '播放异常，请切换线路或重试', icon: 'none' });
};

const togglePlay = () => {
  if (!videoCtx.value) return;
  if (paused.value) {
    videoCtx.value.play();
    paused.value = false;
  } else {
    videoCtx.value.pause();
    paused.value = true;
  }
};

const seekBy = (delta) => {
  if (!videoCtx.value) return;
  const target = Math.max(0, Math.min((duration.value || 0), currentTime.value + delta));
  videoCtx.value.seek(target);
  currentTime.value = target;
};

const normalizeEpisodes = (episodes) => {
  if (!Array.isArray(episodes) || !episodes.length) {
    return [{ index: 1, name: '01' }];
  }

  return episodes.map((item, idx) => ({
    index: Number(item.index) || idx + 1,
    name: String(item.name || idx + 1).slice(0, 8).padStart(2, '0')
  }));
};

const loadVideoResource = async (ep = currentEp.value) => {
  const seq = requestSeq.value + 1;
  requestSeq.value = seq;
  loading.value = true;
  buffering.value = true;

  if (videoCtx.value) {
    videoCtx.value.pause();
  }

  const cacheKey = `${videoId.value}:${ep}`;
  const cachedPlayInfo = playInfoCache.get(cacheKey);
  const playInfo = cachedPlayInfo || await request({
    url: `/app/video/play/${videoId.value}`,
    data: { ep }
  });

  if (playInfo && !cachedPlayInfo) {
    playInfoCache.set(cacheKey, playInfo);
  }

  // 快速切集时，丢弃已经过期的响应，避免旧地址覆盖新地址造成反复重载和卡顿。
  if (seq !== requestSeq.value) return;
  if (!playInfo) {
    loading.value = false;
    buffering.value = false;
    return;
  }

  videoTitle.value = playInfo.title || '视频播放';
  posterUrl.value = playInfo.posterUrl || '/static/default-poster.png';
  episodeList.value = normalizeEpisodes(playInfo.episodes);
  currentEp.value = Number(playInfo.currentEp) || ep || 1;
  const nextUrl = playInfo.currentUrl || '';
  const shouldReload = nextUrl !== playUrl.value;
  if (shouldReload) {
    playUrl.value = '';
    await nextTick();
    playUrl.value = nextUrl;
    playerKey.value += 1;
  }

  const format = (playInfo.format || '').toLowerCase();
  isM3u8.value = format.includes('m3u8') || (playUrl.value || '').includes('.m3u8');

  if (!playUrl.value) {
    uni.showToast({ title: '暂无可播放地址', icon: 'none' });
  }

  await nextTick();
  initVideoCtx();
  paused.value = false;
  buffering.value = Boolean(playUrl.value);
  initialTime.value = 0;
  currentTime.value = 0;
  lastUiUpdateAt.value = 0;
  loading.value = false;
};

onLoad(async (options) => {
  videoId.value = options.id || '';
  currentEp.value = Number(options.ep) || 1;

  if (!videoId.value) {
    uni.showToast({ title: '参数缺失', icon: 'none' });
    return;
  }

  try {
    await loadVideoResource();
  } catch (e) {
    loading.value = false;
    buffering.value = false;
    console.error('播放页加载失败', e);
  }
});

const changeEpisode = async (ep) => {
  if (currentEp.value === ep) return;
  const target = episodeList.value.find((item) => item.index === ep);
  if (!target) return;
  if (loading.value) return;
  try {
    await loadVideoResource(ep);
  } catch (e) {
    loading.value = false;
    buffering.value = false;
    console.error('切换剧集失败', e);
  }
};
</script>

<style scoped>
.play-container { min-height: 100vh; background-color: #0f131d; padding-top: 100rpx; }
.video-wrapper { width: 100%; height: 460rpx; background-color: #000; }
.video-player { width: 100%; height: 100%; }
.quick-controls { display: flex; align-items: center; gap: 12rpx; padding: 20rpx; background: #151b28; }
.ctrl { background: #212a3a; color: #dce2ef; border-radius: 8rpx; font-size: 22rpx; height: 60rpx; line-height: 60rpx; padding: 0 18rpx; }
.buffering { color: #f0c35b; font-size: 22rpx; margin-left: auto; }
.time { color: #8f9bb3; font-size: 22rpx; margin-left: 12rpx; }
.control-panel { padding: 26rpx; color: #fff; }
.playing-title { font-size: 34rpx; font-weight: 600; margin-bottom: 20rpx; display: block; color: #18d96b; }
.section { margin-bottom: 26rpx; }
.section-title { font-size: 28rpx; font-weight: 600; margin-bottom: 14rpx; display: block; color: #cfd7e8; }
.m3u8-tag { color: #18d96b; font-size: 22rpx; background: rgba(24, 217, 107, 0.12); padding: 8rpx 14rpx; border-radius: 8rpx; }
.episode-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: 12rpx; }
.ep-item { background-color: #1a2332; color: #b4c0d7; text-align: center; padding: 16rpx 0; font-size: 22rpx; border-radius: 8rpx; }
.ep-item.active { background-color: #18d96b; color: #fff; font-weight: bold; }
.ep-item.disabled { opacity: 0.5; pointer-events: none; }
</style>
