package com.devicehub.api.dto;

import com.devicehub.api.domain.DeviceState;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request body for updating a device (all fields optional for PATCH)")
public record DeviceUpdateRequest(

        @Schema(description = "Device name", example = "MacBook Pro 16")
        String name,

        @Schema(description = "Device brand", example = "Apple")
        String brand,

        @Schema(description = "Device state", example = "IN_USE")
        DeviceState state
) {}
