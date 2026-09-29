package com.devicehub.api.assessment

import java.time.LocalDate

data class WarrantyInfo(
    val covered: Boolean,
    val provider: String,
    val expiresOn: LocalDate?
)
