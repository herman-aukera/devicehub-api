package com.devicehub.api.service;

import com.devicehub.api.domain.Device;
import com.devicehub.api.domain.DeviceState;
import com.devicehub.api.dto.DeviceCreateRequest;
import com.devicehub.api.dto.DeviceResponse;
import com.devicehub.api.repository.DeviceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @InjectMocks
    private DeviceService deviceService;

    @Test
    void shouldCreateDevice_whenValidRequestProvided() {
        // Given - valid creation request
        DeviceCreateRequest request = new DeviceCreateRequest(
                "MacBook Pro",
                "Apple",
                DeviceState.AVAILABLE
        );

        Device savedDevice = Device.builder()
                .id(1L)
                .name("MacBook Pro")
                .brand("Apple")
                .state(DeviceState.AVAILABLE)
                .creationTime(LocalDateTime.now())
                .build();

        when(deviceRepository.save(any(Device.class))).thenReturn(savedDevice);

        // When - creating device
        DeviceResponse response = deviceService.create(request);

        // Then - device should be saved and response returned
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("MacBook Pro");
        assertThat(response.brand()).isEqualTo("Apple");
        assertThat(response.state()).isEqualTo(DeviceState.AVAILABLE);
        assertThat(response.creationTime()).isNotNull();

        verify(deviceRepository).save(any(Device.class));
    }

    @Test
    void shouldSetCreationTimeToNow_whenCreatingDevice() {
        // Given - creation request
        DeviceCreateRequest request = new DeviceCreateRequest(
                "iPhone 15",
                "Apple",
                DeviceState.IN_USE
        );

        LocalDateTime now = LocalDateTime.now();
        Device savedDevice = Device.builder()
                .id(2L)
                .name("iPhone 15")
                .brand("Apple")
                .state(DeviceState.IN_USE)
                .creationTime(now)
                .build();

        when(deviceRepository.save(any(Device.class))).thenReturn(savedDevice);

        // When - creating device
        DeviceResponse response = deviceService.create(request);

        // Then - creation time should be set
        assertThat(response.creationTime()).isNotNull();
        assertThat(response.creationTime()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    void shouldMapAllFieldsCorrectly_whenCreatingDevice() {
        // Given - creation request with INACTIVE state
        DeviceCreateRequest request = new DeviceCreateRequest(
                "Old Laptop",
                "Dell",
                DeviceState.INACTIVE
        );

        Device savedDevice = Device.builder()
                .id(3L)
                .name("Old Laptop")
                .brand("Dell")
                .state(DeviceState.INACTIVE)
                .creationTime(LocalDateTime.now())
                .build();

        when(deviceRepository.save(any(Device.class))).thenReturn(savedDevice);

        // When - creating device
        DeviceResponse response = deviceService.create(request);

        // Then - all fields should be mapped correctly
        assertThat(response.name()).isEqualTo(request.name());
        assertThat(response.brand()).isEqualTo(request.brand());
        assertThat(response.state()).isEqualTo(request.state());
    }
}
