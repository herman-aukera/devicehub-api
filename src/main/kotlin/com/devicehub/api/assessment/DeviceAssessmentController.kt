package com.devicehub.api.assessment

import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/devices")
class DeviceAssessmentController(private val assessmentService: DeviceAssessmentService) {
    @GetMapping("/{id}/assessment")
    @Operation(summary = "Assess device with partner data")
    suspend fun assess(@PathVariable id: Long): ResponseEntity<DeviceAssessment> =
        ResponseEntity.ok(assessmentService.assess(id))
}
