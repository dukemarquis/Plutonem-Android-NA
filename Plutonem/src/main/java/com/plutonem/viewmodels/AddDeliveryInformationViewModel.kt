/*
 * Copyright 2019 The Plutonem Application Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.plutonem.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plutonem.data.DeliveryInformationRepository
import kotlinx.coroutines.launch

/**
 * The ViewModel used in [AddDeliveryInformationFragment].
 */
class AddDeliveryInformationViewModel(
        private val deliveryInformationRepository: DeliveryInformationRepository
): ViewModel() {

    fun addDeliveryInformation(deliveryPerson: String, deliveryNumber: String, deliveryAddress: String) {
        viewModelScope.launch {
            deliveryInformationRepository.addDeliveryInformation(deliveryPerson, deliveryNumber, deliveryAddress)
        }
    }

    fun deleteAllDeliveryInformation() {
        viewModelScope.launch {
            deliveryInformationRepository.deleteAllDeliveryInformation()
        }
    }
}