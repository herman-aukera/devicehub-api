package com.devicehub.api.dto

import java.time.LocalDate

data class WarrantyInfo(
    val covered: Boolean,
    val provider: String,
    val expiresOn: LocalDate?
)
