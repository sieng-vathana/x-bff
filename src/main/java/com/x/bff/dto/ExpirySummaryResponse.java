package com.x.bff.dto;

public record ExpirySummaryResponse(
        long expiredBatchCount,
        long expiredUnits,
        long within7DaysBatchCount,
        long within7DaysUnits,
        long within30DaysBatchCount,
        long within30DaysUnits,
        long undatedBatchCount,
        long undatedUnits) {
}
