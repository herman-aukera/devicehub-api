package com.devicehub.api.service;

import com.devicehub.api.domain.Device;
import com.devicehub.api.dto.DeviceCreateRequest;
import com.devicehub.api.dto.DeviceResponse;
import com.devicehub.api.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
