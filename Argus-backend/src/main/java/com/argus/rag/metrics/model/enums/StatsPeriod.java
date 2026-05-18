package com.argus.rag.metrics.model.enums;

import java.time.LocalDate;
import java.time.LocalDateTime;

public enum StatsPeriod {
    TODAY(0),
    LAST_7_DAYS(7),
    LAST_14_DAYS(14),
    LAST_30_DAYS(30);

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
