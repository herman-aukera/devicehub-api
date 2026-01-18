package com.devicehub.api.controller;

import com.devicehub.api.domain.DeviceState;
import com.devicehub.api.dto.DeviceCreateRequest;
import com.devicehub.api.dto.DeviceResponse;
import com.devicehub.api.dto.DeviceUpdateRequest;
import com.devicehub.api.exception.BusinessRuleViolationException;
import com.devicehub.api.exception.DeviceNotFoundException;
import com.devicehub.api.service.DeviceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DeviceController.class)
class DeviceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DeviceService deviceService;

    // === CREATE OPERATION TESTS ===

    @Test
    void shouldCreateDevice_whenValidRequestProvided() throws Exception {
        // Given - valid creation request
        DeviceCreateRequest request = new DeviceCreateRequest(
                "MacBook Pro",
                "Apple",
                DeviceState.AVAILABLE
        );

        DeviceResponse response = new DeviceResponse(
                1L,
                "MacBook Pro",
                "Apple",
                DeviceState.AVAILABLE,
                LocalDateTime.now()
        );

        when(deviceService.create(any(DeviceCreateRequest.class))).thenReturn(response);

        // When & Then - should return 201 with Location header
        mockMvc.perform(post("/api/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("Location", containsString("/api/devices/1")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("MacBook Pro"))
                .andExpect(jsonPath("$.brand").value("Apple"))
                .andExpect(jsonPath("$.state").value("AVAILABLE"));
    }

    @Test
    void shouldReturn400_whenCreatingDeviceWithInvalidData() throws Exception {
        // Given - invalid request with blank name
        DeviceCreateRequest request = new DeviceCreateRequest(
                "",  // blank name
                "Apple",
                DeviceState.AVAILABLE
        );

        // When & Then - should return 400
        mockMvc.perform(post("/api/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // === READ OPERATION TESTS ===

    @Test
    void shouldGetDevice_whenDeviceExists() throws Exception {
        // Given - existing device
        DeviceResponse response = new DeviceResponse(
                1L,
                "MacBook Pro",
                "Apple",
                DeviceState.AVAILABLE,
                LocalDateTime.now()
        );

        when(deviceService.findById(1L)).thenReturn(response);

        // When & Then - should return 200 with device
        mockMvc.perform(get("/api/devices/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("MacBook Pro"))
                .andExpect(jsonPath("$.brand").value("Apple"))
                .andExpect(jsonPath("$.state").value("AVAILABLE"));
    }

    @Test
    void shouldReturn404_whenDeviceNotFound() throws Exception {
        // Given - non-existent device
        when(deviceService.findById(999L)).thenThrow(new DeviceNotFoundException(999L));

        // When & Then - should return 404
        mockMvc.perform(get("/api/devices/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldListAllDevices_whenNoFilterProvided() throws Exception {
        // Given - multiple devices
        List<DeviceResponse> devices = List.of(
                new DeviceResponse(1L, "MacBook Pro", "Apple", DeviceState.AVAILABLE, LocalDateTime.now()),
                new DeviceResponse(2L, "iPhone 15", "Apple", DeviceState.IN_USE, LocalDateTime.now())
        );

        when(deviceService.findAll()).thenReturn(devices);

        // When & Then - should return 200 with all devices
        mockMvc.perform(get("/api/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));
    }

    @Test
    void shouldListDevicesByBrand_whenBrandFilterProvided() throws Exception {
        // Given - devices filtered by brand
        List<DeviceResponse> devices = List.of(
                new DeviceResponse(1L, "MacBook Pro", "Apple", DeviceState.AVAILABLE, LocalDateTime.now())
        );

        when(deviceService.findByBrand("Apple")).thenReturn(devices);

        // When & Then - should return 200 with filtered devices
        mockMvc.perform(get("/api/devices")
                        .param("brand", "Apple"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].brand").value("Apple"));
    }

    @Test
    void shouldListDevicesByState_whenStateFilterProvided() throws Exception {
        // Given - devices filtered by state
        List<DeviceResponse> devices = List.of(
                new DeviceResponse(2L, "iPhone 15", "Apple", DeviceState.IN_USE, LocalDateTime.now())
        );

        when(deviceService.findByState(DeviceState.IN_USE)).thenReturn(devices);

        // When & Then - should return 200 with filtered devices
        mockMvc.perform(get("/api/devices")
                        .param("state", "IN_USE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].state").value("IN_USE"));
    }

    // === UPDATE OPERATION TESTS ===

    @Test
    void shouldUpdateDevice_whenValidRequestProvided() throws Exception {
        // Given - valid update request
        DeviceUpdateRequest request = new DeviceUpdateRequest(
                "MacBook Pro 16",
                "Apple",
                DeviceState.INACTIVE
        );

        DeviceResponse response = new DeviceResponse(
                1L,
                "MacBook Pro 16",
                "Apple",
                DeviceState.INACTIVE,
                LocalDateTime.now()
        );

        when(deviceService.update(eq(1L), any(DeviceUpdateRequest.class))).thenReturn(response);

        // When & Then - should return 200 with updated device
        mockMvc.perform(put("/api/devices/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("MacBook Pro 16"))
                .andExpect(jsonPath("$.state").value("INACTIVE"));
    }

    @Test
    void shouldReturn409_whenUpdateViolatesBusinessRule() throws Exception {
        // Given - update that violates business rule
        DeviceUpdateRequest request = new DeviceUpdateRequest(
                "New Name",
                "Apple",
                DeviceState.IN_USE
        );

        when(deviceService.update(eq(1L), any(DeviceUpdateRequest.class)))
                .thenThrow(new BusinessRuleViolationException("Cannot update name or brand when device state is IN_USE"));

        // When & Then - should return 409
        mockMvc.perform(put("/api/devices/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldPartiallyUpdateDevice_whenValidRequestProvided() throws Exception {
        // Given - partial update request
        DeviceUpdateRequest request = new DeviceUpdateRequest(
                null,  // name not provided
                null,  // brand not provided
                DeviceState.INACTIVE
        );

        DeviceResponse response = new DeviceResponse(
                1L,
                "MacBook Pro",
                "Apple",
                DeviceState.INACTIVE,
                LocalDateTime.now()
        );

        when(deviceService.partialUpdate(eq(1L), any(DeviceUpdateRequest.class))).thenReturn(response);

        // When & Then - should return 200 with updated device
        mockMvc.perform(patch("/api/devices/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.state").value("INACTIVE"));
    }

    // === DELETE OPERATION TESTS ===

    @Test
    void shouldDeleteDevice_whenDeviceExists() throws Exception {
        // Given - device exists
        // (no mock needed, void method)

        // When & Then - should return 204
        mockMvc.perform(delete("/api/devices/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldReturn409_whenDeletingInUseDevice() throws Exception {
        // Given - device is IN_USE
        doThrow(new BusinessRuleViolationException("Cannot delete device with state IN_USE"))
                .when(deviceService).delete(1L);

        // When & Then - should return 409
        mockMvc.perform(delete("/api/devices/1"))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturn404_whenDeletingNonExistentDevice() throws Exception {
        // Given - device doesn't exist
        doThrow(new DeviceNotFoundException(999L))
                .when(deviceService).delete(999L);

        // When & Then - should return 404
        mockMvc.perform(delete("/api/devices/999"))
                .andExpect(status().isNotFound());
    }
}
