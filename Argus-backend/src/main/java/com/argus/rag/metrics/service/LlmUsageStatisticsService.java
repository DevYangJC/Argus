package com.argus.rag.metrics.service;

import com.argus.rag.metrics.mapper.LlmUsageRecordMapper;
import com.argus.rag.metrics.model.enums.StatsPeriod;
import com.argus.rag.metrics.model.vo.MetricsOverviewVO;
import com.argus.rag.metrics.model.vo.UsageRankItemVO;
import com.argus.rag.metrics.model.vo.UsageStatsVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LlmUsageStatisticsService {

    private final LlmUsageRecordMapper llmUsageRecordMapper;

    public LlmUsageStatisticsService(LlmUsageRecordMapper llmUsageRecordMapper) {
        this.llmUsageRecordMapper = llmUsageRecordMapper;
    }

    /** 用户级别统计 */
    public UsageStatsVO getUserStats(Long userId, StatsPeriod period) {
        return llmUsageRecordMapper.selectUsageStats(userId, null, period.getStartTime());
    }

    /** 群组级别统计 */
    public UsageStatsVO getGroupStats(Long groupId, StatsPeriod period) {
        return llmUsageRecordMapper.selectUsageStats(null, groupId, period.getStartTime());
    }

    /** 平台级别统计（管理员） */
    public UsageStatsVO getPlatformStats(StatsPeriod period) {
        return llmUsageRecordMapper.selectUsageStats(null, null, period.getStartTime());
    }

    /** 仪表盘概览 */
    public MetricsOverviewVO getOverview() {
        MetricsOverviewVO overview = new MetricsOverviewVO();

        // 今日统计
        UsageStatsVO todayStats = getPlatformStats(StatsPeriod.TODAY);
        overview.setTodayRequests(todayStats.getTotalRequests());
        overview.setTodayTokens(todayStats.getTotalTokens());
        overview.setTodayCost(todayStats.getTotalCost());
        overview.setTodaySuccessRate(todayStats.getSuccessRate());

        // 30天趋势
        overview.setDailyTrend(llmUsageRecordMapper.selectDailyTrend(
                StatsPeriod.LAST_30_DAYS.getStartTime(), null));

        return overview;
    }

    /** 每日趋势数据 */
    public List<MetricsOverviewVO.DailyStats> getDailyTrend(StatsPeriod period, String module) {
        return llmUsageRecordMapper.selectDailyTrend(period.getStartTime(), module);
    }

    /** 用户排行 */
    public List<UsageRankItemVO> getUserRank(StatsPeriod period, int limit) {
        return llmUsageRecordMapper.selectUserRank(period.getStartTime(), limit);
    }

    /** 群组排行 */
    public List<UsageRankItemVO> getGroupRank(StatsPeriod period, int limit) {
        return llmUsageRecordMapper.selectGroupRank(period.getStartTime(), limit);
    }
}
