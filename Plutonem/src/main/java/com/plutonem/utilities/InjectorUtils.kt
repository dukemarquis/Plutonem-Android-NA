package com.plutonem.utilities

import android.content.Context
import com.plutonem.data.AppDatabase
import com.plutonem.data.DeliveryInformationRepository
import com.plutonem.viewmodels.AddDeliveryInformationViewModelFactory
import com.plutonem.viewmodels.DeliveryInformationViewModelFactory

/**
 * Static methods used to inject classes needed for various Activities and Fragments.
 */
object InjectorUtils {

    private fun getDeliveryInformationRepository(context: Context): DeliveryInformationRepository {
        return DeliveryInformationRepository.getInstance(
                AppDatabase.getInstance(context.applicationContext).deliveryInformationDao())
    }

    fun provideDeliveryInformationViewModelFactory(
            context: Context
    ): DeliveryInformationViewModelFactory {
        val repository = getDeliveryInformationRepository(context)
        return DeliveryInformationViewModelFactory(repository)
    }

    fun provideAddDeliveryInformationViewModelFactory(
            context: Context
    ): AddDeliveryInformationViewModelFactory {
        return AddDeliveryInformationViewModelFactory(
                getDeliveryInformationRepository(context))
    }
}
