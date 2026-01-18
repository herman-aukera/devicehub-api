package com.devicehub.api.controller;

import com.devicehub.api.domain.DeviceState;
import com.devicehub.api.dto.DeviceCreateRequest;
import com.devicehub.api.dto.DeviceResponse;
import com.devicehub.api.dto.DeviceUpdateRequest;
import com.devicehub.api.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * REST controller for device resource management.
 * Provides endpoints for CRUD operations on devices with proper HTTP semantics.
 */
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Device Management", description = "Endpoints for managing device resources")
public class DeviceController {

    private final DeviceService deviceService;

    /**
     * Create a new device.
     *
     * @param request the device creation request
     * @return the created device with 201 status and Location header
     */
    @PostMapping
    @Operation(
            summary = "Create a new device",
            description = "Creates a new device with the provided details",
            responses = {
                    @ApiResponse(
                            responseCode = "201",
                            description = "Device created successfully",
                            content = @Content(schema = @Schema(implementation = DeviceResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid request data"
                    )
            }
    )
    public ResponseEntity<DeviceResponse> createDevice(
            @Valid @RequestBody DeviceCreateRequest request) {
        log.info("POST /api/devices - Creating device: name={}, brand={}", 
                request.name(), request.brand());

        DeviceResponse response = deviceService.create(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    /**
     * Get a device by ID.
     *
     * @param id the device ID
     * @return the device with 200 status
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "Get device by ID",
            description = "Retrieves a single device by its unique identifier",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Device found",
                            content = @Content(schema = @Schema(implementation = DeviceResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Device not found"
                    )
            }
    )
    public ResponseEntity<DeviceResponse> getDevice(
            @Parameter(description = "Device ID", required = true)
            @PathVariable Long id) {
        log.info("GET /api/devices/{} - Fetching device", id);

        DeviceResponse response = deviceService.findById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Get all devices or filter by brand/state.
     *
     * @param brand optional brand filter
     * @param state optional state filter
     * @return list of devices with 200 status
     */
    @GetMapping
    @Operation(
            summary = "List all devices",
            description = "Retrieves all devices, optionally filtered by brand or state",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Devices retrieved successfully"
                    )
            }
    )
    public ResponseEntity<List<DeviceResponse>> listDevices(
            @Parameter(description = "Filter by brand (case-insensitive)")
            @RequestParam(required = false) String brand,
            @Parameter(description = "Filter by state")
            @RequestParam(required = false) DeviceState state) {
        log.info("GET /api/devices - Listing devices: brand={}, state={}", brand, state);

        List<DeviceResponse> devices;

        if (brand != null) {
            devices = deviceService.findByBrand(brand);
        } else if (state != null) {
            devices = deviceService.findByState(state);
        } else {
            devices = deviceService.findAll();
        }

        return ResponseEntity.ok(devices);
    }

    /**
     * Full update of a device.
     *
     * @param id the device ID
     * @param request the update request with all fields
     * @return the updated device with 200 status
     */
    @PutMapping("/{id}")
    @Operation(
            summary = "Update device",
            description = "Performs a full update of a device. Cannot update name/brand when device is IN_USE.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Device updated successfully",
                            content = @Content(schema = @Schema(implementation = DeviceResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Device not found"
                    ),
                    @ApiResponse(
                            responseCode = "409",
                            description = "Business rule violation (e.g., updating IN_USE device)"
                    )
            }
    )
    public ResponseEntity<DeviceResponse> updateDevice(
            @Parameter(description = "Device ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody DeviceUpdateRequest request) {
        log.info("PUT /api/devices/{} - Updating device", id);

        DeviceResponse response = deviceService.update(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Partial update of a device (PATCH).
     *
     * @param id the device ID
     * @param request the update request with optional fields
     * @return the updated device with 200 status
     */
    @PatchMapping("/{id}")
    @Operation(
            summary = "Partially update device",
            description = "Updates only the provided fields. Cannot update name/brand when device is IN_USE.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Device updated successfully",
                            content = @Content(schema = @Schema(implementation = DeviceResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Device not found"
                    ),
                    @ApiResponse(
                            responseCode = "409",
                            description = "Business rule violation"
                    )
            }
    )
    public ResponseEntity<DeviceResponse> partialUpdateDevice(
            @Parameter(description = "Device ID", required = true)
            @PathVariable Long id,
            @RequestBody DeviceUpdateRequest request) {
        log.info("PATCH /api/devices/{} - Partially updating device", id);

        DeviceResponse response = deviceService.partialUpdate(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete a device by ID.
     *
     * @param id the device ID
     * @return 204 No Content on successful deletion
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Delete device",
            description = "Deletes a device. Cannot delete devices with state IN_USE.",
            responses = {
                    @ApiResponse(
                            responseCode = "204",
                            description = "Device deleted successfully"
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Device not found"
                    ),
                    @ApiResponse(
                            responseCode = "409",
                            description = "Business rule violation (e.g., deleting IN_USE device)"
                    )
            }
    )
    public ResponseEntity<Void> deleteDevice(
            @Parameter(description = "Device ID", required = true)
            @PathVariable Long id) {
        log.info("DELETE /api/devices/{} - Deleting device", id);

        deviceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
