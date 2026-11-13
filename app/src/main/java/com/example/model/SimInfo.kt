package com.example.model

data class SimInfo(
    val subscriptionId: Int,
    val simSlotIndex: Int,
    val carrierName: String,
    val displayName: String,
    val number: String?,
    val countryIso: String?,
    val operatorNumeric: String?,
    val isDefaultSms: Boolean = false,
    val smscNumber: String? = null
)
