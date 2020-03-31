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

package com.plutonem.data

class DeliveryInformationRepository private constructor(
        private val deliveryInformationDao: DeliveryInformationDao
) {

    suspend fun addDeliveryInformation(deliveryPerson: String, deliveryNumber: String, deliverAddress: String) {
        val deliveryInformation = DeliveryInformation(deliveryPerson, deliveryNumber, deliverAddress)
        deliveryInformationDao.insertDeliveryInformation(deliveryInformation)
    }

    suspend fun deleteAllDeliveryInformation() {
        deliveryInformationDao.deleteAllDeliveryInformation()
    }

    fun getDeliveryInformation() = deliveryInformationDao.getDeliveryInformation()

    companion object {

        // For Singleton instantiation
        @Volatile private var instance: DeliveryInformationRepository? = null

        fun getInstance(deliveryInformationDao: DeliveryInformationDao) =
                instance ?: synchronized(this) {
                    instance ?: DeliveryInformationRepository(deliveryInformationDao).also { instance = it }
                }
    }
}