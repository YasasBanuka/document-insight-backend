package com.webdynamo.document_insight.util;

import jakarta.servlet.http.HttpServletRequest;

public class RequestUtils {

    /**
     * Get client IP address from request, handling proxies
     */
    public static String getClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
