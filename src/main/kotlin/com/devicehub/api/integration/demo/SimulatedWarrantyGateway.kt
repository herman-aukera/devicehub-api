package com.devicehub.api.integration.demo

import com.devicehub.api.dto.WarrantyInfo
import com.devicehub.api.integration.WarrantyGateway
import kotlinx.coroutines.delay
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class SimulatedWarrantyGateway : WarrantyGateway {
    override suspend fun getWarranty(deviceId: Long): WarrantyInfo {
        delay(350)
        return WarrantyInfo(
            covered = true,
            provider = "DeviceProtect",
            expiresOn = LocalDate.parse("2027-12-31")
        )
    }
}
