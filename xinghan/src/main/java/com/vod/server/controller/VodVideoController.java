package com.vod.server.controller;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.vod.server.utils.Result;
import com.vod.server.entity.domain.VodVideo;
import com.vod.server.service.IVodVideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@RestController
@RequestMapping("/app/video")
@RequiredArgsConstructor
@Tag(name = "APP视频接口")
public class VodVideoController {
    private final IVodVideoService vodVideoService;
    @GetMapping("/list")
    @Operation(summary = "获取视频列表（分页+分类筛选）")
    public Result<IPage<VodVideo>> list(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String year,
            @RequestParam(required = false, defaultValue = "最新") String sort,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        IPage<VodVideo> pageResult = vodVideoService.getVideoPage(categoryId, type, region, year, sort, page, size);
        return Result.success(pageResult);
    }
    @GetMapping("/detail/{id}")
    @Operation(summary = "获取视频详情")
    public Result<VodVideo> detail(@PathVariable Long id) {
        VodVideo video = vodVideoService.getVideoDetail(id);
        return video != null ? Result.success(video) : Result.error("视频不存在");
    }
    @GetMapping("/play/{id}")
    @Operation(summary = "获取视频播放地址（按集返回当前播放地址）")
    public Result<Map<String, Object>> getPlayUrl(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "1") Integer ep) {
        VodVideo video = vodVideoService.getOne(
                new LambdaQueryWrapper<VodVideo>()
                        .eq(VodVideo::getId, id)
                        .select(
                                VodVideo::getId,
                                VodVideo::getTitle,
                                VodVideo::getPlayUrl,
                                VodVideo::getPlayFormat,
                                VodVideo::getPosterUrl
                        ),
                false
        );
        if (video == null) {
            return Result.error("该视频不存在或已下架");
        }
        List<Map<String, Object>> episodes = parseEpisodes(video.getPlayUrl());
        if (episodes.isEmpty()) {
            return Result.error("暂无可播放地址");
        }

        int targetEp = ep == null || ep < 1 || ep > episodes.size() ? 1 : ep;
        Map<String, Object> currentEpisode = episodes.get(targetEp - 1);

        Map<String, Object> result = new HashMap<>();
        result.put("title", video.getTitle());
        result.put("currentEp", targetEp);
        result.put("currentUrl", currentEpisode.get("url"));
        result.put("episodes", episodes.stream()
                .map(item -> {
                    Map<String, Object> episode = new HashMap<>();
                    episode.put("index", item.get("index"));
                    episode.put("name", item.get("name"));
                    return episode;
                })
                .toList());
        result.put("format", video.getPlayFormat());
        result.put("posterUrl", video.getPosterUrl());
        return Result.success(result);
    }

    private List<Map<String, Object>> parseEpisodes(String playUrlRaw) {
        List<Map<String, Object>> episodes = new ArrayList<>();
        if (playUrlRaw == null || playUrlRaw.trim().isEmpty()) {
            return episodes;
        }

        String[] parts = playUrlRaw.split("#");
        for (String part : parts) {
            String item = part == null ? "" : part.trim();
            if (item.isEmpty()) {
                continue;
            }

            String[] pair = item.split("\\$", 2);
            String name = pair.length > 1 ? pair[0].trim() : String.valueOf(episodes.size() + 1);
            String url = pair.length > 1 ? pair[1].trim() : pair[0].trim();
            if (url.isEmpty()) {
                continue;
            }

            Map<String, Object> episode = new HashMap<>();
            int index = episodes.size() + 1;
            episode.put("index", index);
            episode.put("name", name.isEmpty() ? String.format("%02d", index) : name);
            episode.put("url", url);
            episodes.add(episode);
        }
        return episodes;
    }
    @GetMapping("/banner")
    @Operation(summary = "首页轮播图海报")
    public Result<List<VodVideo>> getBanners() {
        // 简单策略：取最近更新的 5 部带有海报的视频作为轮播图
        List<VodVideo> banners = vodVideoService.list(
                new LambdaQueryWrapper<VodVideo>()
                        .isNotNull(VodVideo::getPosterUrl)
                        .orderByDesc(VodVideo::getUpdateTime)
                        .last("LIMIT 5")
        );
        return Result.success(banners);
    }

    @GetMapping("/recommend")
    @Operation(summary = "视频详情页底部-猜你喜欢")
    public Result<List<VodVideo>> getRecommend(
            @RequestParam(required = false) Long currentVideoId,
            @RequestParam(required = false) Long categoryId) {
        // 这里的具体实现在 Service 层，留出优化空间
        List<VodVideo> recommends = vodVideoService.getRecommendations(currentVideoId, categoryId);
        return Result.success(recommends);
    }
}
