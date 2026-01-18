package com.devicehub.api.dto;

import com.devicehub.api.domain.DeviceState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Device response with all fields")
public record DeviceResponse(

        @Schema(description = "Unique identifier", example = "1")
        Long id,

        @Schema(description = "Device name", example = "MacBook Pro")
        String name,

        @Schema(description = "Device brand", example = "Apple")
        String brand,

        @Schema(description = "Current device state", example = "AVAILABLE")
        DeviceState state,

        @Schema(description = "Timestamp when device was created", example = "2026-01-18T16:30:00")
        LocalDateTime creationTime
) {}
