package com.plutonem.ui.products

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.plutonem.R
import com.plutonem.ui.utils.UiString
import com.plutonem.ui.utils.UiString.UiStringRes
import com.plutonem.ui.products.AccountFilterSelection.EVERYONE

data class OrderListMainViewState(
    val isAccountFilterVisible: Boolean,
    val accountFilterSelection: AccountFilterSelection,
    val accountFilterItems: List<AccountFilterListItemUIState>
)

sealed class AccountFilterListItemUIState(
        val id: Long,
        val text: UiString,
        @ColorRes open val dropDownBackground: Int
) {
    data class Everyone(@ColorRes override val dropDownBackground: Int, @DrawableRes val imageRes: Int) :
            AccountFilterListItemUIState(
                    id = AccountFilterSelection.EVERYONE.id,
                    text = UiStringRes(R.string.order_list_account_me),
                    dropDownBackground = dropDownBackground
            )
}

fun getAccountFilterItems(
        selection: AccountFilterSelection
): List<AccountFilterListItemUIState> {
    return AccountFilterSelection.values().map { value ->
        @ColorRes val backgroundColorRes: Int =
                if (selection == value) R.color.brown_lighten_30_translucent_50
                else R.color.transparent

        when (value) {
            EVERYONE -> AccountFilterListItemUIState.Everyone(
                    backgroundColorRes,
                    R.drawable.bg_oval_neutral_30_multiple_users_white_40dp
            )
        }
    }
}