package com.devicehub.api.dto

data class DeviceAssessment(
    val device: DeviceResponse,
    val telemetry: TelemetrySnapshot,
    val warranty: WarrantyInfo
)
