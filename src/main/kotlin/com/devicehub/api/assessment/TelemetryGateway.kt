package com.devicehub.api.assessment

interface TelemetryGateway {
    suspend fun getTelemetry(deviceId: Long): TelemetrySnapshot
}
