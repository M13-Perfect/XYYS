<template>
  <view class="play-container">
    <global-nav />

    <view class="video-wrapper">
      <video
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
        object-fit="contain"
        @timeupdate="onTimeUpdate"
        @loadedmetadata="onLoadedMeta"
        @play="onPlay"
        @pause="onPause"
        @waiting="onWaiting"
        @canplay="onCanPlay"
        @error="onVideoError"
        @ended="onEnded"
      ></video>
      <view class="video-mask" v-if="isLoading || isBuffering || playError">
        <text class="status-text error-text" v-if="playError">{{ playError }}</text>
        <text class="status-text" v-else>{{ isLoading ? '视频加载中...' : '视频缓冲中...' }}</text>
        <button class="retry-btn" v-if="playError" @click="retryLoad">重试</button>
      </view>
    </view>

    <view class="quick-controls">
      <button class="ctrl" :disabled="isLoading || !!playError" @click="seekBy(-10)">后退10s</button>
      <button class="ctrl" :disabled="isLoading || !!playError" @click="togglePlay">{{ paused ? '播放' : '暂停' }}</button>
      <button class="ctrl" :disabled="isLoading || !!playError" @click="seekBy(10)">快进10s</button>
      <text class="time">{{ formatTime(currentTime) }} / {{ formatTime(duration) }}</text>
    </view>

    <view class="control-panel">
      <text class="playing-title">{{ videoTitle }} - 第 {{ currentEp }} 集</text>

      <view class="section" v-if="isM3u8">
        <text class="m3u8-tag">当前播放格式：M3U8</text>
      </view>

      <view class="section">
        <text class="section-title">选集</text>
        <view class="episode-grid">
          <view class="ep-item" :class="{ active: currentEp === ep.index, disabled: isLoading }" v-for="ep in episodeList" :key="ep.index" @click="changeEpisode(ep.index)">
            {{ ep.name }}
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { nextTick, ref } from 'vue';
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
const paused = ref(false);
const currentTime = ref(0);
const duration = ref(0);
const isLoading = ref(false);
const isBuffering = ref(false);
const playError = ref('');
let lastTimeUpdateAt = 0;

const initVideoCtx = () => {
  videoCtx.value = uni.createVideoContext('myVideo');
};

const onTimeUpdate = (e) => {
  const now = Date.now();
  if (now - lastTimeUpdateAt < 500) return;
  lastTimeUpdateAt = now;
  currentTime.value = Number(e.detail.currentTime || 0);
};

const onLoadedMeta = (e) => {
  duration.value = Number(e.detail.duration || 0);
};

const onPlay = () => {
  paused.value = false;
  playError.value = '';
};

const onPause = () => {
  paused.value = true;
};

const onWaiting = () => {
  isBuffering.value = true;
};

const onCanPlay = () => {
  isLoading.value = false;
  isBuffering.value = false;
  playError.value = '';
};

const onVideoError = () => {
  isLoading.value = false;
  isBuffering.value = false;
  paused.value = true;
  playError.value = '视频加载失败，请检查播放源或稍后重试';
};

const onEnded = () => {
  paused.value = true;
  isBuffering.value = false;
};

const formatTime = (sec) => {
  const s = Math.max(0, Math.floor(sec || 0));
  const m = Math.floor(s / 60).toString().padStart(2, '0');
  const ss = (s % 60).toString().padStart(2, '0');
  return `${m}:${ss}`;
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
  if (videoCtx.value) {
    videoCtx.value.pause();
  }
  isLoading.value = true;
  isBuffering.value = false;
  playError.value = '';
  paused.value = true;
  currentTime.value = 0;
  duration.value = 0;
  lastTimeUpdateAt = 0;

  let playInfo = null;
  try {
    playInfo = await request({
      url: `/app/video/play/${videoId.value}`,
      data: { ep }
    });
  } catch (e) {
    isLoading.value = false;
    isBuffering.value = false;
    playError.value = '播放信息加载失败，请稍后重试';
    throw e;
  }
  if (!playInfo) {
    isLoading.value = false;
    playError.value = '播放信息为空，请稍后重试';
    return;
  }

  videoTitle.value = playInfo.title || '视频播放';
  posterUrl.value = playInfo.posterUrl || '/static/default-poster.png';
  episodeList.value = normalizeEpisodes(playInfo.episodes);
  currentEp.value = Number(playInfo.currentEp) || ep || 1;
  playUrl.value = playInfo.currentUrl || '';

  const format = (playInfo.format || '').toLowerCase();
  isM3u8.value = format.includes('m3u8') || (playUrl.value || '').includes('.m3u8');

  if (!playUrl.value) {
    isLoading.value = false;
    playError.value = '暂无可播放地址';
    uni.showToast({ title: '暂无可播放地址', icon: 'none' });
  }

  await nextTick();
  initVideoCtx();
  paused.value = false;
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
    console.error('播放页加载失败', e);
  }
});

const changeEpisode = async (ep) => {
  if (currentEp.value === ep || isLoading.value) return;
  const target = episodeList.value.find((item) => item.index === ep);
  if (!target) return;
  try {
    await loadVideoResource(ep);
  } catch (e) {
    console.error('切换剧集失败', e);
  }
};

const retryLoad = async () => {
  try {
    await loadVideoResource(currentEp.value);
  } catch (e) {
    console.error('重试加载失败', e);
  }
};
</script>

<style scoped>
.play-container { min-height: 100vh; background-color: #0f131d; padding-top: 100rpx; }
.video-wrapper { width: 100%; height: 460rpx; background-color: #000; position: relative; }
.video-player { width: 100%; height: 100%; }
.video-mask { position: absolute; top: 0; left: 0; right: 0; bottom: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; background: rgba(0,0,0,0.55); z-index: 2; }
.status-text { color: #dce2ef; font-size: 26rpx; margin-bottom: 18rpx; }
.error-text { color: #ff7875; }
.retry-btn { background: #18d96b; color: #fff; border-radius: 8rpx; font-size: 24rpx; height: 58rpx; line-height: 58rpx; padding: 0 28rpx; }
.quick-controls { display: flex; align-items: center; gap: 12rpx; padding: 20rpx; background: #151b28; }
.ctrl { background: #212a3a; color: #dce2ef; border-radius: 8rpx; font-size: 22rpx; height: 60rpx; line-height: 60rpx; padding: 0 18rpx; }
.ctrl[disabled] { opacity: 0.5; }
.time { color: #8f9bb3; font-size: 22rpx; margin-left: auto; }
.control-panel { padding: 26rpx; color: #fff; }
.playing-title { font-size: 34rpx; font-weight: 600; margin-bottom: 20rpx; display: block; color: #18d96b; }
.section { margin-bottom: 26rpx; }
.section-title { font-size: 28rpx; font-weight: 600; margin-bottom: 14rpx; display: block; color: #cfd7e8; }
.m3u8-tag { color: #18d96b; font-size: 22rpx; background: rgba(24, 217, 107, 0.12); padding: 8rpx 14rpx; border-radius: 8rpx; }
.episode-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: 12rpx; }
.ep-item { background-color: #1a2332; color: #b4c0d7; text-align: center; padding: 16rpx 0; font-size: 22rpx; border-radius: 8rpx; }
.ep-item.active { background-color: #18d96b; color: #fff; font-weight: bold; }
.ep-item.disabled { opacity: 0.55; }
</style>
