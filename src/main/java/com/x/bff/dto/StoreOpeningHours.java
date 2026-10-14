package com.x.bff.dto;

import java.time.DayOfWeek;
import java.util.Map;

public record StoreOpeningHours(
        String timeZone,
        Map<DayOfWeek, DaySchedule> days) {

    public record DaySchedule(
            boolean closed,
            String openTime,
            String closeTime) {
    }
}
