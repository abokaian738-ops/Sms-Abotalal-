package com.example.model

enum class DeliveryStatus(val code: Int) {
    NONE(0),
    PENDING(1),
    DELIVERED(2),
    FAILED(3),
    SENT(4);

    companion object {
        fun fromCode(code: Int): DeliveryStatus =
            entries.find { it.code == code } ?: NONE
    }
}
