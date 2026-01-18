package com.devicehub.api.integration;

import com.devicehub.api.domain.DeviceState;
import com.devicehub.api.dto.DeviceCreateRequest;
import com.devicehub.api.dto.DeviceResponse;
import com.devicehub.api.dto.DeviceUpdateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration test verifying complete API workflows.
 * Tests the full stack: Controller -> Service -> Repository -> Database
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeviceApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCompleteFullDeviceLifecycle() throws Exception {
        // === STEP 1: CREATE A DEVICE ===
        DeviceCreateRequest createRequest = new DeviceCreateRequest(
                "MacBook Pro 16",
                "Apple",
                DeviceState.AVAILABLE
        );

        MvcResult createResult = mockMvc.perform(post("/api/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("MacBook Pro 16"))
                .andExpect(jsonPath("$.brand").value("Apple"))
                .andExpect(jsonPath("$.state").value("AVAILABLE"))
                .andExpect(jsonPath("$.creationTime").exists())
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        DeviceResponse createdDevice = objectMapper.readValue(responseBody, DeviceResponse.class);
        Long deviceId = createdDevice.id();
        assertThat(deviceId).isNotNull();

        // === STEP 2: GET THE DEVICE BY ID ===
        mockMvc.perform(get("/api/devices/" + deviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deviceId))
                .andExpect(jsonPath("$.name").value("MacBook Pro 16"))
                .andExpect(jsonPath("$.brand").value("Apple"))
                .andExpect(jsonPath("$.state").value("AVAILABLE"));

        // === STEP 3: UPDATE THE DEVICE (set to IN_USE) ===
        DeviceUpdateRequest updateRequest = new DeviceUpdateRequest(
                "MacBook Pro 16",
                "Apple",
                DeviceState.IN_USE
        );

        mockMvc.perform(put("/api/devices/" + deviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IN_USE"));

        // === STEP 4: TRY TO UPDATE NAME WHEN IN_USE (should fail) ===
        DeviceUpdateRequest invalidUpdateRequest = new DeviceUpdateRequest(
                "MacBook Pro 14",  // Trying to change name
                "Apple",
                DeviceState.IN_USE
        );

        mockMvc.perform(put("/api/devices/" + deviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidUpdateRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Business Rule Violation"))
                .andExpect(jsonPath("$.detail").value(containsString("Cannot update name or brand")));

        // === STEP 5: TRY TO DELETE WHEN IN_USE (should fail) ===
        mockMvc.perform(delete("/api/devices/" + deviceId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Business Rule Violation"))
                .andExpect(jsonPath("$.detail").value(containsString("Cannot delete device")));

        // === STEP 6: CHANGE STATE TO INACTIVE ===
        DeviceUpdateRequest inactiveRequest = new DeviceUpdateRequest(
                null,  // Name not changed
                null,  // Brand not changed
                DeviceState.INACTIVE
        );

        mockMvc.perform(patch("/api/devices/" + deviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inactiveRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("INACTIVE"))
                .andExpect(jsonPath("$.name").value("MacBook Pro 16"));  // Unchanged

        // === STEP 7: DELETE THE DEVICE (now allowed) ===
        mockMvc.perform(delete("/api/devices/" + deviceId))
                .andExpect(status().isNoContent());

        // === STEP 8: VERIFY DEVICE IS DELETED ===
        mockMvc.perform(get("/api/devices/" + deviceId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Device Not Found"));
    }

    @Test
    void shouldFilterDevicesByBrand() throws Exception {
        // Create Apple device
        DeviceCreateRequest appleDevice = new DeviceCreateRequest(
                "iPhone 15",
                "Apple",
                DeviceState.AVAILABLE
        );

        mockMvc.perform(post("/api/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(appleDevice)))
                .andExpect(status().isCreated());

        // Create Samsung device
        DeviceCreateRequest samsungDevice = new DeviceCreateRequest(
                "Galaxy S24",
                "Samsung",
                DeviceState.AVAILABLE
        );

        mockMvc.perform(post("/api/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(samsungDevice)))
                .andExpect(status().isCreated());

        // Filter by brand (case-insensitive)
        mockMvc.perform(get("/api/devices")
                        .param("brand", "apple"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[*].brand", everyItem(equalToIgnoringCase("Apple"))));
    }

    @Test
    void shouldFilterDevicesByState() throws Exception {
        // Create IN_USE device
        DeviceCreateRequest inUseDevice = new DeviceCreateRequest(
                "Test Device",
                "TestBrand",
                DeviceState.IN_USE
        );

        mockMvc.perform(post("/api/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inUseDevice)))
                .andExpect(status().isCreated());

        // Filter by state
        mockMvc.perform(get("/api/devices")
                        .param("state", "IN_USE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[*].state", everyItem(is("IN_USE"))));
    }

    @Test
    void shouldRejectInvalidDeviceCreation() throws Exception {
        // Blank name
        DeviceCreateRequest invalidRequest = new DeviceCreateRequest(
                "",  // Blank name
                "Apple",
                DeviceState.AVAILABLE
        );

        mockMvc.perform(post("/api/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void shouldReturn404ForNonExistentDevice() throws Exception {
        mockMvc.perform(get("/api/devices/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Device Not Found"))
                .andExpect(jsonPath("$.detail").value(containsString("Device not found with id: 99999")));
    }
}
