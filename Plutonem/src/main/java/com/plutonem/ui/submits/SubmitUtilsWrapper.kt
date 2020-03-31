package com.plutonem.ui.submits

import android.app.Activity
import android.view.View
import com.plutonem.android.fluxc.Dispatcher
import com.plutonem.android.fluxc.model.BuyerModel
import com.plutonem.android.fluxc.model.OrderModel
import com.plutonem.utilities.SnackbarSequencer
import dagger.Reusable
import javax.inject.Inject

/**
 * Injectable wrapper around ConfirmUtils.
 *
 * ConfirmUtils interface is consisted of static methods, which makes the client code difficult to test/mock.
 * Main purpose of this wrapper is to make testing easier.
 */
@Reusable
class SubmitUtilsWrapper @Inject constructor(
        private val sequencer: SnackbarSequencer
) {
    fun onOrderSubmittedSnackbarHandler(
            activity: Activity?,
            snackbarAttachView: View?,
            isError: Boolean,
            order: OrderModel?,
            errorMessage: String?,
            buyer: BuyerModel?
    ) = ConfirmUtils.onOrderSubmittedSnackbarHandler(
            activity,
            snackbarAttachView,
            isError,
            order,
            errorMessage,
            buyer,
            sequencer
    )
}