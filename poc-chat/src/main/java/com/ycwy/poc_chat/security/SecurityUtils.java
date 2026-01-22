package com.ycwy.poc_chat.security;

import io.jsonwebtoken.Claims;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.GrantedAuthority;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static String currentUserId() {
        Claims claims = currentClaims();
        return claims == null ? null : claims.get("uid", String.class);
    }

    public static String currentRole() {
        Claims claims = currentClaims();
        if (claims != null) {
            return claims.get("role", String.class);
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String role = authority.getAuthority();
            if (role != null && role.startsWith("ROLE_")) {
                return role.substring("ROLE_".length());
            }
        }
        return null;
    }

    public static String currentEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getName();
    }

    private static Claims currentClaims() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object details = authentication.getDetails();
        if (details instanceof Claims claims) {
            return claims;
        }
        return null;
    }
}
