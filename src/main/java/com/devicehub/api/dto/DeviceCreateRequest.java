package com.devicehub.api.dto;

import com.devicehub.api.domain.DeviceState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request body for creating a new device")
public record DeviceCreateRequest(

        @Schema(description = "Device name", example = "MacBook Pro")
        @NotBlank(message = "Name is required")
        String name,

        @Schema(description = "Device brand", example = "Apple")
        @NotBlank(message = "Brand is required")
        String brand,

        @Schema(description = "Initial device state", example = "AVAILABLE")
        @NotNull(message = "State is required")
        DeviceState state
) {}
