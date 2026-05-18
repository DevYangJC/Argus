package com.argus.rag.metrics.controller;

import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.common.api.ApiResponse;
import com.argus.rag.common.log.OperationLog;
import com.argus.rag.metrics.model.enums.StatsPeriod;
import com.argus.rag.metrics.model.vo.MetricsOverviewVO;
import com.argus.rag.metrics.model.vo.UsageRankItemVO;
import com.argus.rag.metrics.model.vo.UsageStatsVO;
import com.argus.rag.metrics.service.LlmUsageStatisticsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@OperationLog
@RestController
@RequestMapping("/api/admin/metrics")
public class AdminMetricsController {

    private final CurrentUserService currentUserService;
    private final LlmUsageStatisticsService statisticsService;

    public AdminMetricsController(CurrentUserService currentUserService,
                                  LlmUsageStatisticsService statisticsService) {
        this.currentUserService = currentUserService;
        this.statisticsService = statisticsService;
    }

    /** 仪表盘概览 */
    @GetMapping("/overview")
    public ApiResponse<MetricsOverviewVO> getOverview() {
        currentUserService.requireSystemAdmin();
        return ApiResponse.success(statisticsService.getOverview());
    }

    /** 平台整体统计 */
    @GetMapping("/platform")
    public ApiResponse<UsageStatsVO> getPlatformStats(
            @RequestParam(defaultValue = "TODAY") StatsPeriod period) {
        currentUserService.requireSystemAdmin();
        return ApiResponse.success(statisticsService.getPlatformStats(period));
    }

    /** 用户级别统计 */
    @GetMapping("/user/{userId}")
    public ApiResponse<UsageStatsVO> getUserStats(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "TODAY") StatsPeriod period) {
        currentUserService.requireSystemAdmin();
        return ApiResponse.success(statisticsService.getUserStats(userId, period));
    }

    /** 群组级别统计 */
    @GetMapping("/group/{groupId}")
    public ApiResponse<UsageStatsVO> getGroupStats(
            @PathVariable Long groupId,
            @RequestParam(defaultValue = "TODAY") StatsPeriod period) {
        currentUserService.requireSystemAdmin();
        return ApiResponse.success(statisticsService.getGroupStats(groupId, period));
    }

    /** 趋势数据（用于图表） */
    @GetMapping("/trend")
    public ApiResponse<List<MetricsOverviewVO.DailyStats>> getTrend(
            @RequestParam(defaultValue = "LAST_30_DAYS") StatsPeriod period,
            @RequestParam(required = false) String module) {
        currentUserService.requireSystemAdmin();
        return ApiResponse.success(statisticsService.getDailyTrend(period, module));
    }

    /** 用户排行 */
    @GetMapping("/rank/users")
    public ApiResponse<List<UsageRankItemVO>> getUserRank(
            @RequestParam(defaultValue = "LAST_30_DAYS") StatsPeriod period,
            @RequestParam(defaultValue = "20") int limit) {
        currentUserService.requireSystemAdmin();
        return ApiResponse.success(statisticsService.getUserRank(period, limit));
    }

    /** 群组排行 */
    @GetMapping("/rank/groups")
    public ApiResponse<List<UsageRankItemVO>> getGroupRank(
            @RequestParam(defaultValue = "LAST_30_DAYS") StatsPeriod period,
            @RequestParam(defaultValue = "20") int limit) {
        currentUserService.requireSystemAdmin();
        return ApiResponse.success(statisticsService.getGroupRank(period, limit));
    }
}
