package com.ycwy.poc_chat.support.dto;

import java.time.Instant;

public record MessageDto(
        String id,
        String content,
        Instant sentAt,
        String threadId,
        String senderUserId,
        String senderName,
        String senderEmail
) {
}
