package com.devicehub.api.assessment

import com.devicehub.api.dto.DeviceResponse

data class DeviceAssessment(
    val device: DeviceResponse,
    val telemetry: TelemetrySnapshot,
    val warranty: WarrantyInfo
)
