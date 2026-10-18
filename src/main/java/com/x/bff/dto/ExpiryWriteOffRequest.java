package com.x.bff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ExpiryWriteOffRequest(
        @Positive long quantity,
        @NotBlank @Size(max = 500) String reason,
        @Positive Long performedBy) {
}
