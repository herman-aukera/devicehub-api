package com.devicehub.api.assessment

import com.devicehub.api.domain.Device
import com.devicehub.api.domain.DeviceState
import com.devicehub.api.repository.DeviceRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeviceAssessmentControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var deviceRepository: DeviceRepository

    @BeforeEach
    fun setUp() {
        deviceRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        deviceRepository.deleteAll()
    }

    @Test
    fun assessmentEndpointReturnsCombinedResponse() {
        val saved = deviceRepository.saveAndFlush(
            Device.builder()
                .name("MacBook Pro")
                .brand("Apple")
                .state(DeviceState.AVAILABLE)
                .build()
        )

        val result = mockMvc.perform(get("/api/devices/{id}/assessment", saved.id))
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.device.id").value(saved.id))
            .andExpect(jsonPath("$.device.name").value("MacBook Pro"))
            .andExpect(jsonPath("$.telemetry.status").value("ONLINE"))
            .andExpect(jsonPath("$.telemetry.batteryPercent").value(87))
            .andExpect(jsonPath("$.warranty.covered").value(true))
            .andExpect(jsonPath("$.warranty.provider").value("DeviceProtect"))
            .andExpect(jsonPath("$.warranty.expiresOn").value("2027-12-31"))
    }
}
