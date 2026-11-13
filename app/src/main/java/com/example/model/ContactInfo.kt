package com.example.model

data class ContactInfo(
    val id: Long,
    val name: String,
    val phoneNumber: String,
    val normalizedNumber: String,
    val label: String? = null
)
