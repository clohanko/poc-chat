package com.ycwy.poc_chat.reservation.dto;

import java.time.LocalDateTime;

public record ReservationDto(
        String id,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String status,
        int totalPriceCents,
        String currency,
        String carCategoryCode
) {
}
