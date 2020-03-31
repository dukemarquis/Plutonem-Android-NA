package com.plutonem.ui.products.editor

import androidx.annotation.StringRes
import com.plutonem.R
import com.plutonem.android.fluxc.model.order.OrderStatus
import dagger.Reusable
import javax.inject.Inject

@Reusable
class EditorActionsProvider @Inject constructor() {
    fun getPrimaryAction(orderStatus: OrderStatus, userCanConfirm: Boolean): PrimaryEditorAction {
        return when (orderStatus) {
                    OrderStatus.UNKNOWN,
                    OrderStatus.FINISHED,
                    OrderStatus.RECEIVING,
                    OrderStatus.DELIVERING,
                    OrderStatus.PAYING -> PrimaryEditorAction.CONFIRM_NOW
            }
    }
}

enum class PrimaryEditorAction(@StringRes val titleResource: Int) {
    CONFIRM_NOW(R.string.button_confirm),
}