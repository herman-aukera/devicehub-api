package com.devicehub.api.repository;

import com.devicehub.api.domain.Device;
import com.devicehub.api.domain.DeviceState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class DeviceRepositoryTest {

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void shouldFindDevicesByBrand_whenBrandExists() {
        // Given - devices with different brands
        Device apple1 = createDevice("MacBook Pro", "Apple", DeviceState.AVAILABLE);
        Device apple2 = createDevice("iPhone 15", "Apple", DeviceState.IN_USE);
        Device samsung = createDevice("Galaxy S24", "Samsung", DeviceState.AVAILABLE);
        
        entityManager.persist(apple1);
        entityManager.persist(apple2);
        entityManager.persist(samsung);
        entityManager.flush();

        // When - searching by brand (case-insensitive)
        List<Device> appleDevices = deviceRepository.findByBrandIgnoreCase("apple");

        // Then - should return only Apple devices
        assertThat(appleDevices).hasSize(2);
        assertThat(appleDevices).extracting(Device::getBrand)
                .containsOnly("Apple");
    }

    @Test
    void shouldFindDevicesByState_whenStateMatches() {
        // Given - devices with different states
        Device available1 = createDevice("iPad Pro", "Apple", DeviceState.AVAILABLE);
        Device available2 = createDevice("Surface Pro", "Microsoft", DeviceState.AVAILABLE);
        Device inUse = createDevice("ThinkPad X1", "Lenovo", DeviceState.IN_USE);
        Device inactive = createDevice("Old Laptop", "Dell", DeviceState.INACTIVE);
        
        entityManager.persist(available1);
        entityManager.persist(available2);
        entityManager.persist(inUse);
        entityManager.persist(inactive);
        entityManager.flush();

        // When - searching by state
        List<Device> availableDevices = deviceRepository.findByState(DeviceState.AVAILABLE);

        // Then - should return only devices with AVAILABLE state
        assertThat(availableDevices).hasSize(2);
        assertThat(availableDevices).extracting(Device::getState)
                .containsOnly(DeviceState.AVAILABLE);
    }

    @Test
    void shouldReturnEmptyList_whenNoBrandMatches() {
        // Given - devices with known brands
        Device device = createDevice("MacBook Pro", "Apple", DeviceState.AVAILABLE);
        entityManager.persist(device);
        entityManager.flush();

        // When - searching for non-existent brand
        List<Device> devices = deviceRepository.findByBrandIgnoreCase("NonExistentBrand");

        // Then - should return empty list
        assertThat(devices).isEmpty();
    }

    @Test
    void shouldReturnEmptyList_whenNoStateMatches() {
        // Given - devices with AVAILABLE state only
        Device device = createDevice("MacBook Pro", "Apple", DeviceState.AVAILABLE);
        entityManager.persist(device);
        entityManager.flush();

        // When - searching for INACTIVE state
        List<Device> devices = deviceRepository.findByState(DeviceState.INACTIVE);

        // Then - should return empty list
        assertThat(devices).isEmpty();
    }

    @Test
    void shouldBeCaseInsensitive_whenSearchingByBrand() {
        // Given - device with "Apple" brand
        Device device = createDevice("MacBook Pro", "Apple", DeviceState.AVAILABLE);
        entityManager.persist(device);
        entityManager.flush();

        // When - searching with different cases
        List<Device> upperCase = deviceRepository.findByBrandIgnoreCase("APPLE");
        List<Device> lowerCase = deviceRepository.findByBrandIgnoreCase("apple");
        List<Device> mixedCase = deviceRepository.findByBrandIgnoreCase("ApPlE");

        // Then - all should return the same device
        assertThat(upperCase).hasSize(1);
        assertThat(lowerCase).hasSize(1);
        assertThat(mixedCase).hasSize(1);
        assertThat(upperCase.get(0).getId()).isEqualTo(device.getId());
        assertThat(lowerCase.get(0).getId()).isEqualTo(device.getId());
        assertThat(mixedCase.get(0).getId()).isEqualTo(device.getId());
    }

    private Device createDevice(String name, String brand, DeviceState state) {
        return Device.builder()
                .name(name)
                .brand(brand)
                .state(state)
                .build();
    }
}
