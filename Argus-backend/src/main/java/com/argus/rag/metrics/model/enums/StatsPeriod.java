package com.argus.rag.metrics.model.enums;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 统计时间段枚举。
 * <p>定义用量统计的时间范围，支持今日、近7天、近14天和近30天。</p>
 */
public enum StatsPeriod {

    /** 今日（从当天00:00开始） */
    TODAY(0),

    /** 近7天 */
    LAST_7_DAYS(7),

    /** 近14天 */
    LAST_14_DAYS(14),

    /** 近30天 */
    LAST_30_DAYS(30);

    /** 统计天数 */
    private final int days;

    StatsPeriod(int days) { this.days = days; }

    public int getDays() { return days; }

    /**
     * 获取时间段的起始时间。
     */
    public LocalDateTime getStartTime() {
        if (this == TODAY) {
            return LocalDate.now().atStartOfDay();
        }
        return LocalDate.now().minusDays(days).atStartOfDay();
    }
}
