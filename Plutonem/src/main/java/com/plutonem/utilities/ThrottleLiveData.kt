package com.plutonem.utilities

import androidx.lifecycle.MediatorLiveData
import kotlinx.coroutines.*

class ThrottleLiveData<T> constructor(
        private val offset: Long = 100,
        private val coroutineScope: CoroutineScope,
        private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
        private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) :
        MediatorLiveData<T>() {
    private var tempValue: T? = null
    private var currentJob: Job? = null

    override fun postValue(value: T) {
        if (tempValue == null || tempValue != value) {
            currentJob?.cancel()
            currentJob = coroutineScope.launch(backgroundDispatcher) {
                tempValue = value
                delay(offset)
                withContext(mainDispatcher) {
                    tempValue = null
                    super.postValue(value)
                }
            }
        }
    }
}