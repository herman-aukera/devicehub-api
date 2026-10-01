package com.devicehub.api.dto

import java.time.Instant

enum class TelemetryStatus {
    ONLINE,
    DEGRADED,
    OFFLINE
}

data class TelemetrySnapshot(
    val status: TelemetryStatus,
    val batteryPercent: Int,
    val lastSeenAt: Instant
)
