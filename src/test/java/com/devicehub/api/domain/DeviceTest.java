package com.devicehub.api.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class DeviceTest {

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void shouldSetCreationTimeAutomatically_whenDeviceIsCreated() {
        // Given - Device without creationTime set
        Device device = Device.builder()
                .name("iPhone 15")
                .brand("Apple")
                .state(DeviceState.AVAILABLE)
                .build();

        assertThat(device.getCreationTime()).isNull();

        // When - persisting device
        Device savedDevice = entityManager.persistAndFlush(device);

        // Then - creationTime should be auto-populated
        assertThat(savedDevice.getCreationTime()).isNotNull();
        assertThat(savedDevice.getCreationTime()).isBeforeOrEqualTo(java.time.LocalDateTime.now());
    }

    @Test
    void shouldNotUpdateCreationTime_whenDeviceIsModified() {
        // Given - persisted device
        Device device = Device.builder()
                .name("iPad Pro")
                .brand("Apple")
                .state(DeviceState.AVAILABLE)
                .build();

        Device savedDevice = entityManager.persistAndFlush(device);
        Long deviceId = savedDevice.getId();

        entityManager.clear();

        // When - updating device
        Device foundDevice = entityManager.find(Device.class, deviceId);
        var originalCreationTime = foundDevice.getCreationTime();
        foundDevice.setName("iPad Pro 12.9");
        entityManager.flush();
        entityManager.clear();
        Device reloadedDevice = entityManager.find(Device.class, deviceId);

        // Then - creationTime should remain unchanged
        assertThat(reloadedDevice.getCreationTime()).isEqualTo(originalCreationTime);
    }
}
