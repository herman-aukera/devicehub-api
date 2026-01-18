package com.devicehub.api.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
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
        var originalCreationTime = savedDevice.getCreationTime();
        
        entityManager.clear();

        // When - updating device
        Device foundDevice = entityManager.find(Device.class, savedDevice.getId());
        foundDevice.setName("iPad Pro 12.9");
        entityManager.persistAndFlush(foundDevice);

        // Then - creationTime should remain unchanged
        assertThat(foundDevice.getCreationTime()).isEqualTo(originalCreationTime);
    }
}
