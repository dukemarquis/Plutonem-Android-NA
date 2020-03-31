package com.plutonem.ui.products

enum class OrderListViewLayoutType(val id: Long) {
    STANDARD(id = 0);

    companion object {
        @JvmStatic
        val defaultValue = STANDARD

        @JvmStatic
        fun fromId(id: Long): OrderListViewLayoutType {
            return values().firstOrNull { it.id == id } ?: defaultValue
        }
    }
}