package com.bff_vyntra.security;

/**
 * Public client channel for dual-channel authentication (web browser vs mobile app).
 */
public enum ClientChannel {
    WEB,
    MOBILE;

    public static final String HEADER_NAME = "X-Client-Type";

    public static ClientChannel fromHeader(String value) {
        if (value != null && value.trim().equalsIgnoreCase("web")) {
            return WEB;
        }
        return MOBILE;
    }

    public String wireValue() {
        return name().toLowerCase();
    }

    public boolean isWeb() {
        return this == WEB;
    }
}
