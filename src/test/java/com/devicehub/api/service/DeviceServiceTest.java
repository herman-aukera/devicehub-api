package com.devicehub.api.service;

import com.devicehub.api.domain.Device;
import com.devicehub.api.domain.DeviceState;
import com.devicehub.api.dto.DeviceCreateRequest;
import com.devicehub.api.dto.DeviceResponse;
import com.devicehub.api.dto.DeviceUpdateRequest;
import com.devicehub.api.exception.BusinessRuleViolationException;
import com.devicehub.api.exception.DeviceNotFoundException;
import com.devicehub.api.repository.DeviceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    // READ OPERATIONS TESTS

    @Test
    void shouldReturnDevice_whenIdExists() {
        // Given - existing device
        Long deviceId = 1L;
        Device device = Device.builder()
                .id(deviceId)
                .name("MacBook Pro")
                .brand("Apple")
                .state(DeviceState.AVAILABLE)
                .creationTime(LocalDateTime.now())
                .build();

        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(device));

        // When - finding device by ID
        DeviceResponse response = deviceService.findById(deviceId);

        // Then - device should be returned
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(deviceId);
        assertThat(response.name()).isEqualTo("MacBook Pro");
        verify(deviceRepository).findById(deviceId);
    }

    @Test
    void shouldThrowDeviceNotFoundException_whenIdDoesNotExist() {
        // Given - non-existent ID
        Long deviceId = 999L;
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.empty());

        // When & Then - should throw exception
        assertThatThrownBy(() -> deviceService.findById(deviceId))
                .isInstanceOf(DeviceNotFoundException.class)
                .hasMessageContaining("Device not found with id: 999");

        verify(deviceRepository).findById(deviceId);
    }

    @Test
    void shouldReturnAllDevices_whenNoFiltersApplied() {
        // Given - multiple devices
        List<Device> devices = List.of(
                createDevice(1L, "MacBook Pro", "Apple", DeviceState.AVAILABLE),
                createDevice(2L, "iPhone 15", "Apple", DeviceState.IN_USE),
                createDevice(3L, "Galaxy S24", "Samsung", DeviceState.AVAILABLE)
        );

        when(deviceRepository.findAll()).thenReturn(devices);

        // When - finding all devices
        List<DeviceResponse> responses = deviceService.findAll();

        // Then - all devices should be returned
        assertThat(responses).hasSize(3);
        assertThat(responses).extracting(DeviceResponse::name)
                .containsExactly("MacBook Pro", "iPhone 15", "Galaxy S24");
        verify(deviceRepository).findAll();
    }

    @Test
    void shouldReturnFilteredDevices_whenBrandProvided() {
        // Given - devices filtered by brand
        List<Device> appleDevices = List.of(
                createDevice(1L, "MacBook Pro", "Apple", DeviceState.AVAILABLE),
                createDevice(2L, "iPhone 15", "Apple", DeviceState.IN_USE)
        );

        when(deviceRepository.findByBrandIgnoreCase("Apple")).thenReturn(appleDevices);

        // When - finding by brand
        List<DeviceResponse> responses = deviceService.findByBrand("Apple");

        // Then - only Apple devices should be returned
        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(DeviceResponse::brand)
                .containsOnly("Apple");
        verify(deviceRepository).findByBrandIgnoreCase("Apple");
    }

    @Test
    void shouldReturnFilteredDevices_whenStateProvided() {
        // Given - devices filtered by state
        List<Device> availableDevices = List.of(
                createDevice(1L, "MacBook Pro", "Apple", DeviceState.AVAILABLE),
                createDevice(2L, "Galaxy S24", "Samsung", DeviceState.AVAILABLE)
        );

        when(deviceRepository.findByState(DeviceState.AVAILABLE)).thenReturn(availableDevices);

        // When - finding by state
        List<DeviceResponse> responses = deviceService.findByState(DeviceState.AVAILABLE);

        // Then - only AVAILABLE devices should be returned
        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(DeviceResponse::state)
                .containsOnly(DeviceState.AVAILABLE);
        verify(deviceRepository).findByState(DeviceState.AVAILABLE);
    }

    // UPDATE OPERATIONS TESTS

    @Test
    void shouldUpdateDevice_whenStateIsNotInUse() {
        // Given - device with AVAILABLE state
        Long deviceId = 1L;
        Device existingDevice = createDevice(deviceId, "MacBook Pro", "Apple", DeviceState.AVAILABLE);

        DeviceUpdateRequest updateRequest = new DeviceUpdateRequest(
                "MacBook Pro 16", // name changed
                "Apple",
                DeviceState.AVAILABLE
        );

        Device updatedDevice = Device.builder()
                .id(deviceId)
                .name("MacBook Pro 16")
                .brand("Apple")
                .state(DeviceState.AVAILABLE)
                .creationTime(existingDevice.getCreationTime())
                .build();

        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(existingDevice));
        when(deviceRepository.save(any(Device.class))).thenReturn(updatedDevice);

        // When - updating device
        DeviceResponse response = deviceService.update(deviceId, updateRequest);

        // Then - device should be updated
        assertThat(response.name()).isEqualTo("MacBook Pro 16");
        verify(deviceRepository).findById(deviceId);
        verify(deviceRepository).save(any(Device.class));
    }

    @Test
    void shouldThrowBusinessRuleViolationException_whenUpdatingNameWithStateInUse() {
        // Given - device with IN_USE state
        Long deviceId = 1L;
        Device existingDevice = createDevice(deviceId, "MacBook Pro", "Apple", DeviceState.IN_USE);

        DeviceUpdateRequest updateRequest = new DeviceUpdateRequest(
                "MacBook Pro 16", // attempting to change name
                "Apple",
                DeviceState.IN_USE
        );

        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(existingDevice));

        // When & Then - should throw exception
        assertThatThrownBy(() -> deviceService.update(deviceId, updateRequest))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Cannot update name or brand")
                .hasMessageContaining("IN_USE");

        verify(deviceRepository).findById(deviceId);
    }

    @Test
    void shouldThrowBusinessRuleViolationException_whenUpdatingBrandWithStateInUse() {
        // Given - device with IN_USE state
        Long deviceId = 1L;
        Device existingDevice = createDevice(deviceId, "MacBook Pro", "Apple", DeviceState.IN_USE);

        DeviceUpdateRequest updateRequest = new DeviceUpdateRequest(
                "MacBook Pro",
                "Apple Inc", // attempting to change brand
                DeviceState.IN_USE
        );

        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(existingDevice));

        // When & Then - should throw exception
        assertThatThrownBy(() -> deviceService.update(deviceId, updateRequest))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Cannot update name or brand")
                .hasMessageContaining("IN_USE");

        verify(deviceRepository).findById(deviceId);
    }

    @Test
    void shouldAllowStateChange_whenDeviceIsInUse() {
        // Given - device with IN_USE state
        Long deviceId = 1L;
        Device existingDevice = createDevice(deviceId, "MacBook Pro", "Apple", DeviceState.IN_USE);

        DeviceUpdateRequest updateRequest = new DeviceUpdateRequest(
                "MacBook Pro", // same name
                "Apple",       // same brand
                DeviceState.AVAILABLE // state change allowed
        );

        Device updatedDevice = Device.builder()
                .id(deviceId)
                .name("MacBook Pro")
                .brand("Apple")
                .state(DeviceState.AVAILABLE)
                .creationTime(existingDevice.getCreationTime())
                .build();

        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(existingDevice));
        when(deviceRepository.save(any(Device.class))).thenReturn(updatedDevice);

        // When - updating state only
        DeviceResponse response = deviceService.update(deviceId, updateRequest);

        // Then - state should be updated
        assertThat(response.state()).isEqualTo(DeviceState.AVAILABLE);
        verify(deviceRepository).save(any(Device.class));
    }

    @Test
    void shouldIgnoreCreationTime_whenProvidedInUpdateRequest() {
        // Given - device with original creation time
        Long deviceId = 1L;
        LocalDateTime originalCreationTime = LocalDateTime.of(2026, 1, 1, 12, 0);
        Device existingDevice = Device.builder()
                .id(deviceId)
                .name("MacBook Pro")
                .brand("Apple")
                .state(DeviceState.AVAILABLE)
                .creationTime(originalCreationTime)
                .build();

        DeviceUpdateRequest updateRequest = new DeviceUpdateRequest(
                "MacBook Pro Updated",
                "Apple",
                DeviceState.AVAILABLE
        );

        Device updatedDevice = Device.builder()
                .id(deviceId)
                .name("MacBook Pro Updated")
                .brand("Apple")
                .state(DeviceState.AVAILABLE)
                .creationTime(originalCreationTime) // should remain unchanged
                .build();

        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(existingDevice));
        when(deviceRepository.save(any(Device.class))).thenReturn(updatedDevice);

        // When - updating device
        DeviceResponse response = deviceService.update(deviceId, updateRequest);

        // Then - creationTime should not change
        assertThat(response.creationTime()).isEqualTo(originalCreationTime);
    }

    @Test
    void shouldPartiallyUpdateDevice_whenOnlySomeFieldsProvided() {
        // Given - device with all fields
        Long deviceId = 1L;
        Device existingDevice = createDevice(deviceId, "MacBook Pro", "Apple", DeviceState.AVAILABLE);

        DeviceUpdateRequest updateRequest = new DeviceUpdateRequest(
                null,  // name not provided
                null,  // brand not provided
                DeviceState.INACTIVE // only state provided
        );

        Device updatedDevice = Device.builder()
                .id(deviceId)
                .name("MacBook Pro") // unchanged
                .brand("Apple")      // unchanged
                .state(DeviceState.INACTIVE) // changed
                .creationTime(existingDevice.getCreationTime())
                .build();

        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(existingDevice));
        when(deviceRepository.save(any(Device.class))).thenReturn(updatedDevice);

        // When - partially updating device
        DeviceResponse response = deviceService.partialUpdate(deviceId, updateRequest);

        // Then - only state should be updated
        assertThat(response.name()).isEqualTo("MacBook Pro");
        assertThat(response.brand()).isEqualTo("Apple");
        assertThat(response.state()).isEqualTo(DeviceState.INACTIVE);
    }

    @Test
    void shouldThrowBusinessRuleViolationException_whenPartialUpdateChangesNameOnInUseDevice() {
        // Given - device with IN_USE state
        Long deviceId = 1L;
        Device existingDevice = createDevice(deviceId, "MacBook Pro", "Apple", DeviceState.IN_USE);

        DeviceUpdateRequest updateRequest = new DeviceUpdateRequest(
                "MacBook Pro 16", // attempting to change name
                null,             // brand not provided
                null              // state not provided
        );

        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(existingDevice));

        // When & Then - should throw exception
        assertThatThrownBy(() -> deviceService.partialUpdate(deviceId, updateRequest))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Cannot update name or brand")
                .hasMessageContaining("IN_USE");
    }

    // === DELETE OPERATIONS TESTS ===

    @Test
    void shouldDeleteDevice_whenStateIsNotInUse() {
        // Given - device in AVAILABLE state
        Long deviceId = 1L;
        Device device = createDevice(deviceId, "Test Device", "TestBrand", DeviceState.AVAILABLE);

        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(device));

        // When - deleting device
        deviceService.delete(deviceId);

        // Then - device should be deleted
        verify(deviceRepository).delete(device);
    }

    @Test
    void shouldThrowBusinessRuleViolationException_whenDeletingInUseDevice() {
        // Given - device in IN_USE state
        Long deviceId = 1L;
        Device device = createDevice(deviceId, "Test Device", "TestBrand", DeviceState.IN_USE);

        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(device));

        // When & Then - should throw exception
        assertThatThrownBy(() -> deviceService.delete(deviceId))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Cannot delete device")
                .hasMessageContaining("IN_USE");
    }

    @Test
    void shouldThrowDeviceNotFoundException_whenDeletingNonExistentDevice() {
        // Given - non-existent device
        Long deviceId = 999L;

        when(deviceRepository.findById(deviceId)).thenReturn(Optional.empty());

        // When & Then - should throw exception
        assertThatThrownBy(() -> deviceService.delete(deviceId))
                .isInstanceOf(DeviceNotFoundException.class);
    }

    private Device createDevice(Long id, String name, String brand, DeviceState state) {
        return Device.builder()
                .id(id)
                .name(name)
                .brand(brand)
                .state(state)
                .creationTime(LocalDateTime.now())
                .build();
    }
}
