package com.devicehub.api.assessment

import com.devicehub.api.dto.DeviceResponse
import com.devicehub.api.exception.AssessmentTimeoutException
import com.devicehub.api.service.DeviceService
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

private const val PARTNER_TIMEOUT_MS = 1000L

@Service
class DeviceAssessmentService(
    private val deviceService: DeviceService,
    private val telemetryGateway: TelemetryGateway,
    private val warrantyGateway: WarrantyGateway
) {
    private val log = LoggerFactory.getLogger(DeviceAssessmentService::class.java)

    suspend fun assess(deviceId: Long): DeviceAssessment {
        log.info("Starting device assessment: id={}", deviceId)
        val device = deviceService.findById(deviceId)
        val assessment = withTimeoutOrNull(PARTNER_TIMEOUT_MS) {
            assessPartners(deviceId, device)
        } ?: throw AssessmentTimeoutException(deviceId)
        log.info("Device assessment completed: id={}", deviceId)
        return assessment
    }

    private suspend fun assessPartners(deviceId: Long, device: DeviceResponse): DeviceAssessment =
        coroutineScope {
            val telemetry = async { telemetryGateway.getTelemetry(deviceId) }
            val warranty = async { warrantyGateway.getWarranty(deviceId) }
            DeviceAssessment(device, telemetry.await(), warranty.await())
        }
}
