package com.devicehub.api.assessment.demo

import com.devicehub.api.assessment.TelemetryGateway
import com.devicehub.api.assessment.TelemetrySnapshot
import com.devicehub.api.assessment.TelemetryStatus
import kotlinx.coroutines.delay
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class SimulatedTelemetryGateway : TelemetryGateway {
    override suspend fun getTelemetry(deviceId: Long): TelemetrySnapshot {
        delay(200)
        return TelemetrySnapshot(TelemetryStatus.ONLINE, 87, Instant.parse("2026-09-29T12:00:00Z"))
    }
}
