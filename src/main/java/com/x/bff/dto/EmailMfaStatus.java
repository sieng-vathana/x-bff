package com.x.bff.dto;

public record EmailMfaStatus(boolean mailConfigured, boolean emailAvailable, boolean verified,
                             boolean enabled, String maskedEmail) { }
