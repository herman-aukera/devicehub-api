package com.devicehub.api.repository;

import com.devicehub.api.domain.Device;
import com.devicehub.api.domain.DeviceState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Device entity operations.
 * Provides CRUD operations and custom queries for filtering devices.
 */
@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    /**
     * Find all devices by brand (case-insensitive).
     *
     * @param brand the brand name to search for
     * @return list of devices matching the brand
     */
    List<Device> findByBrandIgnoreCase(String brand);

    /**
     * Find all devices by state.
     *
     * @param state the device state to filter by
     * @return list of devices in the specified state
     */
    List<Device> findByState(DeviceState state);
}
