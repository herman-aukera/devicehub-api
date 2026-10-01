package com.devicehub.api.service

import com.devicehub.api.domain.DeviceState
import com.devicehub.api.dto.DeviceAssessment
import com.devicehub.api.dto.DeviceResponse
import com.devicehub.api.dto.TelemetrySnapshot
import com.devicehub.api.dto.TelemetryStatus
import com.devicehub.api.dto.WarrantyInfo
import com.devicehub.api.exception.AssessmentTimeoutException
import com.devicehub.api.exception.PartnerIntegrationException
import com.devicehub.api.integration.TelemetryGateway
import com.devicehub.api.integration.WarrantyGateway
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceAssessmentServiceTest {
    private val device = DeviceResponse(
        1L,
        "MacBook Pro",
        "Apple",
        DeviceState.AVAILABLE,
        LocalDateTime.parse("2026-09-29T12:00:00")
    )

    private val telemetry = TelemetrySnapshot(
        TelemetryStatus.ONLINE,
        87,
        Instant.parse("2026-09-29T12:00:00Z")
    )

    private val warranty = WarrantyInfo(
        true,
        "DeviceProtect",
        LocalDate.parse("2027-12-31")
    )

    private val deviceService = Mockito.mock(DeviceService::class.java).also {
        Mockito.`when`(it.findById(1L)).thenReturn(device)
    }

    private fun createService(
        telemetryCall: suspend () -> TelemetrySnapshot,
        warrantyCall: suspend () -> WarrantyInfo
    ) = DeviceAssessmentService(
        deviceService,
        object : TelemetryGateway {
            override suspend fun getTelemetry(deviceId: Long) = telemetryCall()
        },
        object : WarrantyGateway {
            override suspend fun getWarranty(deviceId: Long) = warrantyCall()
        }
    )

    @Test
    fun shouldReturnCombinedPartnerData_whenAssessmentSucceeds() = runTest {
        // When
        val result = createService({ telemetry }, { warranty }).assess(1L)

        // Then
        assertThat(result.device).isSameAs(device)
        assertThat(result.telemetry).isEqualTo(telemetry)
        assertThat(result.warranty).isEqualTo(warranty)
    }

    @Test
    fun shouldRunPartnerCallsConcurrently_whenAssessingDevice() = runTest {
        // When
        val result = createService(
            { delay(200); telemetry },
            { delay(350); warranty }
        ).assess(1L)

        // Then
        assertThat(result).isEqualTo(DeviceAssessment(device, telemetry, warranty))
        assertThat(currentTime).isEqualTo(350L)
    }

    @Test
    fun shouldCancelSibling_whenPartnerFails() = runTest {
        // Given
        val failure = PartnerIntegrationException(
            "telemetry",
            IllegalStateException("unavailable")
        )
        var siblingCancelled = false
        val assessmentService = createService(
            { delay(100); throw failure },
            {
                try {
                    awaitCancellation()
                } finally {
                    siblingCancelled = true
                }
            }
        )

        // When
        val propagated = runCatching {
            assessmentService.assess(1L)
        }.exceptionOrNull()

        // Then
        assertThat(propagated).isSameAs(failure)
        assertThat(siblingCancelled).isTrue()
    }

    @Test
    fun shouldCancelPartnerCalls_whenAssessmentTimesOut() = runTest {
        // Given
        var telemetryStarted = false
        var warrantyStarted = false
        var telemetryCancelled = false
        var warrantyCancelled = false
        val assessmentService = createService(
            {
                telemetryStarted = true
                try {
                    delay(2000)
                    telemetry
                } finally {
                    telemetryCancelled = true
                }
            },
            {
                warrantyStarted = true
                try {
                    delay(2000)
                    warranty
                } finally {
                    warrantyCancelled = true
                }
            }
        )

        // When
        val timeout = runCatching {
            assessmentService.assess(1L)
        }.exceptionOrNull()

        // Then
        assertThat(timeout).isInstanceOf(AssessmentTimeoutException::class.java)
        assertThat(currentTime).isEqualTo(1000L)
        assertThat(telemetryStarted).isTrue()
        assertThat(warrantyStarted).isTrue()
        assertThat(telemetryCancelled).isTrue()
        assertThat(warrantyCancelled).isTrue()
    }
}
