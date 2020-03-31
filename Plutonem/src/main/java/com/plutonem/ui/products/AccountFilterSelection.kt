package com.plutonem.ui.products

enum class AccountFilterSelection(val id: Long) {
    EVERYONE(id = 1);

    companion object {
        @JvmStatic
        val defaultValue = EVERYONE

        @JvmStatic
        fun fromId(id: Long): AccountFilterSelection {
            return values().firstOrNull { it.id == id } ?: defaultValue
        }
    }
}