package com.devicehub.api.service;

import com.devicehub.api.domain.Device;
import com.devicehub.api.domain.DeviceState;
import com.devicehub.api.dto.DeviceCreateRequest;
import com.devicehub.api.dto.DeviceResponse;
import com.devicehub.api.dto.DeviceUpdateRequest;
import com.devicehub.api.exception.BusinessRuleViolationException;
import com.devicehub.api.exception.DeviceNotFoundException;
import com.devicehub.api.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service layer for device management operations.
 * Handles business logic and DTO transformations.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;

    /**
     * Create a new device.
     *
     * @param request the device creation request
     * @return the created device response
     */
    @Transactional
    public DeviceResponse create(DeviceCreateRequest request) {
        log.info("Creating device: name={}, brand={}, state={}",
                request.name(), request.brand(), request.state());

        Device device = toEntity(request);
        Device savedDevice = deviceRepository.save(device);

        log.info("Device created successfully: id={}", savedDevice.getId());
        return toResponse(savedDevice);
    }

    /**
     * Find a device by ID.
     *
     * @param id the device ID
     * @return the device response
     * @throws DeviceNotFoundException if device not found
     */
    @Transactional(readOnly = true)
    public DeviceResponse findById(Long id) {
        log.debug("Finding device by id={}", id);

        return deviceRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> {
                    log.warn("Device not found: id={}", id);
                    return new DeviceNotFoundException(id);
                });
    }

    /**
     * Find all devices.
     *
     * @return list of all devices
     */
    @Transactional(readOnly = true)
    public List<DeviceResponse> findAll() {
        log.debug("Finding all devices");

        return deviceRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Find devices by brand.
     *
     * @param brand the brand to filter by
     * @return list of devices matching the brand
     */
    @Transactional(readOnly = true)
    public List<DeviceResponse> findByBrand(String brand) {
        log.debug("Finding devices by brand={}", brand);

        return deviceRepository.findByBrandIgnoreCase(brand).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Find devices by state.
     *
     * @param state the state to filter by
     * @return list of devices in the specified state
     */
    @Transactional(readOnly = true)
    public List<DeviceResponse> findByState(DeviceState state) {
        log.debug("Finding devices by state={}", state);

        return deviceRepository.findByState(state).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Full update of a device.
     *
     * @param id the device ID
     * @param request the update request with all fields
     * @return the updated device response
     * @throws DeviceNotFoundException if device not found
     * @throws BusinessRuleViolationException if update violates business rules
     */
    @Transactional
    public DeviceResponse update(Long id, DeviceUpdateRequest request) {
        log.info("Updating device: id={}", id);

        Device existingDevice = deviceRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Device not found: id={}", id);
                    return new DeviceNotFoundException(id);
                });

        validateUpdateAllowed(existingDevice, request);

        // Update all fields (creationTime is immutable in entity)
        existingDevice.setName(request.name());
        existingDevice.setBrand(request.brand());
        existingDevice.setState(request.state());

        Device savedDevice = deviceRepository.save(existingDevice);
        log.info("Device updated successfully: id={}", id);

        return toResponse(savedDevice);
    }

    /**
     * Partial update of a device (PATCH).
     *
     * @param id the device ID
     * @param request the update request with optional fields
     * @return the updated device response
     * @throws DeviceNotFoundException if device not found
     * @throws BusinessRuleViolationException if update violates business rules
     */
    @Transactional
    public DeviceResponse partialUpdate(Long id, DeviceUpdateRequest request) {
        log.info("Partially updating device: id={}", id);

        Device existingDevice = deviceRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Device not found: id={}", id);
                    return new DeviceNotFoundException(id);
                });

        validateUpdateAllowed(existingDevice, request);

        // Update only provided fields
        if (request.name() != null) {
            existingDevice.setName(request.name());
        }
        if (request.brand() != null) {
            existingDevice.setBrand(request.brand());
        }
        if (request.state() != null) {
            existingDevice.setState(request.state());
        }

        Device savedDevice = deviceRepository.save(existingDevice);
        log.info("Device partially updated successfully: id={}", id);

        return toResponse(savedDevice);
    }

    /**
     * Delete a device by ID.
     *
     * @param id the device ID
     * @throws DeviceNotFoundException if device not found
     * @throws BusinessRuleViolationException if device is IN_USE
     */
    @Transactional
    public void delete(Long id) {
        log.info("Deleting device: id={}", id);

        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Device not found: id={}", id);
                    return new DeviceNotFoundException(id);
                });

        if (device.getState() == DeviceState.IN_USE) {
            log.warn("Delete blocked: cannot delete IN_USE device: id={}, state={}",
                    device.getId(), device.getState());
            throw new BusinessRuleViolationException(
                    "Cannot delete device with state IN_USE");
        }

        deviceRepository.delete(device);
        log.info("Device deleted successfully: id={}", id);
    }

    /**
     * Validate if update is allowed based on business rules.
     * Uses pattern matching for cleaner state validation.
     */
    private void validateUpdateAllowed(Device existingDevice, DeviceUpdateRequest request) {
        // Use pattern matching switch for state-based validation
        switch (existingDevice.getState()) {
            case IN_USE -> {
                boolean nameChanged = request.name() != null &&
                        !request.name().equals(existingDevice.getName());
                boolean brandChanged = request.brand() != null &&
                        !request.brand().equals(existingDevice.getBrand());

                if (nameChanged || brandChanged) {
                    log.warn("Update blocked: cannot modify name/brand for IN_USE device: id={}, state={}",
                            existingDevice.getId(), existingDevice.getState());
                    throw new BusinessRuleViolationException(
                            "Cannot update name or brand when device state is IN_USE");
                }
            }
            case AVAILABLE, INACTIVE -> {
                // No restrictions for these states
            }
        }
    }

    /**
     * Convert request DTO to entity.
     */
    private Device toEntity(DeviceCreateRequest request) {
        return Device.builder()
                .name(request.name())
                .brand(request.brand())
                .state(request.state())
                .build();
    }

    /**
     * Convert entity to response DTO.
     */
    private DeviceResponse toResponse(Device device) {
        return new DeviceResponse(
                device.getId(),
                device.getName(),
                device.getBrand(),
                device.getState(),
                device.getCreationTime()
        );
    }
}
