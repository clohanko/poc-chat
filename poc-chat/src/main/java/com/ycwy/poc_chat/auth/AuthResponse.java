package com.ycwy.poc_chat.auth;

public record AuthResponse(
        String token,
        String userId,
        String email,
        String role
) {
}
