package com.plutonem.utilities

import com.plutonem.Plutonem
import dagger.Reusable
import org.wordpress.android.util.NetworkUtils
import javax.inject.Inject

@Reusable
class NetworkUtilsWrapper @Inject constructor() {
    /**
     * Returns true if a network connection is available.
     */
    fun isNetworkAvailable() = NetworkUtils.isNetworkAvailable(Plutonem.getContext())
}