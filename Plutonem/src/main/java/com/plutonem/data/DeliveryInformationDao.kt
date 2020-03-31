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

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * The Data Access Object for the [DeliveryInformation] class.
 */
@Dao
interface DeliveryInformationDao {
    @Query("SELECT * FROM delivery_information")
    fun getDeliveryInformation(): LiveData<List<DeliveryInformation>>

    @Query("DELETE FROM delivery_information")
    suspend fun deleteAllDeliveryInformation()

    @Insert
    suspend fun insertDeliveryInformation(deliveryInformation: DeliveryInformation): Long
}