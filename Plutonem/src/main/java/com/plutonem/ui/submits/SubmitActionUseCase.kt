package com.plutonem.ui.submits

import com.plutonem.android.fluxc.model.OrderImmutableModel
import com.plutonem.ui.submits.SubmitActionUseCase.SubmitAction.DO_NOTHING
import com.plutonem.ui.submits.SubmitActionUseCase.SubmitAction.SUBMIT
import com.plutonem.ui.submits.SubmitActionUseCase.SubmitAction.SUBMIT_AS_PAYING
import dagger.Reusable
import javax.inject.Inject

@Reusable
class SubmitActionUseCase @Inject constructor(
) {
    enum class SubmitAction {
        SUBMIT_AS_PAYING, SUBMIT, DO_NOTHING
    }

    fun getSubmitAction(order: OrderImmutableModel): SubmitAction {
        return when {
            submitWillPushChanges(order) ->
                // We are sure we can push the order as the user has explicitly confirmed the changes
                SUBMIT
            order.isLocalDraft ->
                // Local draft can always be submitted as PAYING as it doesn't exist on the server yet
                SUBMIT_AS_PAYING
            else -> DO_NOTHING
        }
    }

    fun submitWillPushChanges(order: OrderImmutableModel) =
            order.changesConfirmedContentHashcode == order.contentHashcode()
}