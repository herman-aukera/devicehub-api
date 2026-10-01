package com.devicehub.api.integration.demo

import com.devicehub.api.dto.TelemetrySnapshot
import com.devicehub.api.dto.TelemetryStatus
import com.devicehub.api.integration.TelemetryGateway
import kotlinx.coroutines.delay
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class SimulatedTelemetryGateway : TelemetryGateway {
    override suspend fun getTelemetry(deviceId: Long): TelemetrySnapshot {
        delay(200)
        return TelemetrySnapshot(
            status = TelemetryStatus.ONLINE,
            batteryPercent = 87,
            lastSeenAt = Instant.parse("2026-09-29T12:00:00Z")
        )
    }
}
