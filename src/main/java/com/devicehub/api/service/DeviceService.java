package com.devicehub.api.service;

import com.devicehub.api.domain.Device;
import com.devicehub.api.domain.DeviceState;
import com.devicehub.api.dto.DeviceCreateRequest;
import com.devicehub.api.dto.DeviceResponse;
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
