package com.devicehub.api.assessment.demo

import com.devicehub.api.assessment.WarrantyGateway
import com.devicehub.api.assessment.WarrantyInfo
import kotlinx.coroutines.delay
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class SimulatedWarrantyGateway : WarrantyGateway {
    override suspend fun getWarranty(deviceId: Long): WarrantyInfo {
        delay(350)
        return WarrantyInfo(true, "DeviceProtect", LocalDate.parse("2027-12-31"))
    }
}
