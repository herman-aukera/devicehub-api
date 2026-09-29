package com.devicehub.api.assessment

interface WarrantyGateway {
    suspend fun getWarranty(deviceId: Long): WarrantyInfo
}
