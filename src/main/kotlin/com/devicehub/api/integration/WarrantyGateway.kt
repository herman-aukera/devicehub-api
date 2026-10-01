package com.devicehub.api.integration

import com.devicehub.api.dto.WarrantyInfo

interface WarrantyGateway {
    suspend fun getWarranty(deviceId: Long): WarrantyInfo
}
