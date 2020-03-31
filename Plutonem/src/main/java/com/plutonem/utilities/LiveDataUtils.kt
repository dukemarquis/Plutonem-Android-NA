package com.plutonem.utilities

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData

/**
 * This method ensures that the LiveData instance doesn't emit the same item twice
 */
fun <T> LiveData<T>.distinct(): MediatorLiveData<T> {
    val mediatorLiveData: MediatorLiveData<T> = MediatorLiveData()
    mediatorLiveData.addSource(this) {
        if (it != mediatorLiveData.value) {
            mediatorLiveData.value = it
        }
    }
    return mediatorLiveData
}