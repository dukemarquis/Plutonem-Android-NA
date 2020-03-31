package com.plutonem.viewmodels

import com.plutonem.data.DeliveryInformation

class ConfirmOrderViewModel(private val deliveryInformation: DeliveryInformation) {
    val deliveryPerson
        get() = deliveryInformation.deliverPerson
    val deliveryNumber
        get() = deliveryInformation.deliverNumber
    val deliverAddress
        get() = deliveryInformation.deliverAddress
}