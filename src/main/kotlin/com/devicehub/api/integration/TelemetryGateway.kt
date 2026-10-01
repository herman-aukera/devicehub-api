package com.devicehub.api.integration

import com.devicehub.api.dto.TelemetrySnapshot

interface TelemetryGateway {
    suspend fun getTelemetry(deviceId: Long): TelemetrySnapshot
}
