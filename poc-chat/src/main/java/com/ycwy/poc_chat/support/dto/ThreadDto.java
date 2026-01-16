package com.ycwy.poc_chat.support.dto;

import java.time.Instant;

public record ThreadDto(
        String id,
        String subject,
        String status,
        Instant createdAt,
        String createdByUserId,
        String createdByName,
        String createdByEmail,
        String reservationId,
        String assignedSupportUserId,
        String assignedSupportName,
        String assignedSupportEmail
) {
}
