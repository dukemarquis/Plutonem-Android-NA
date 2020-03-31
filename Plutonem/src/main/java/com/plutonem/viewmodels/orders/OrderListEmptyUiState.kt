package com.plutonem.viewmodels.orders

import androidx.annotation.DrawableRes
import com.plutonem.R
import com.plutonem.android.fluxc.store.ListStore.ListError
import com.plutonem.ui.products.OrderListType
import com.plutonem.ui.products.OrderListType.RECEIVING
import com.plutonem.ui.products.OrderListType.DELIVERING
import com.plutonem.ui.products.OrderListType.FINISHED
import com.plutonem.ui.utils.UiString
import com.plutonem.ui.utils.UiString.UiStringRes

sealed class OrderListEmptyUiState(
        val title: UiString? = null,
        @DrawableRes val imgResId: Int? = null,
        val emptyViewVisible: Boolean = true
) {
    class EmptyList(
            title: UiString,
            @DrawableRes imageResId: Int = R.drawable.img_illustration_orders_75dp
    ) : OrderListEmptyUiState(
            title = title,
            imgResId = imageResId
    )

    object DataShown : OrderListEmptyUiState(emptyViewVisible = false)

    object Loading : OrderListEmptyUiState(
            title = UiStringRes(R.string.orders_fetching),
            imgResId = R.drawable.img_illustration_orders_75dp
    )

    class RefreshError(
            title: UiString,
            buttonText: UiString? = null,
            onButtonClick: (() -> Unit)? = null
    ) : OrderListEmptyUiState(
            title = title,
            imgResId = R.drawable.img_illustration_empty_results_216dp
    )
}

fun createEmptyUiState(
        orderListType: OrderListType,
        isNetworkAvailable: Boolean,
        isLoadingData: Boolean,
        isListEmpty: Boolean,
        error: ListError?,
        fetchFirstPage: () -> Unit
): OrderListEmptyUiState {
    return if (isListEmpty) {
        when {
            error != null -> createErrorListUiState(
                    isNetworkAvailable = isNetworkAvailable,
                    error = error,
                    fetchFirstPage = fetchFirstPage
            )
            isLoadingData -> {
                OrderListEmptyUiState.Loading
            }
            else -> createEmptyListUiState(
                    orderListType = orderListType
            )
        }
    } else {
        OrderListEmptyUiState.DataShown
    }
}

private fun createErrorListUiState(
        isNetworkAvailable: Boolean,
        error: ListError,
        fetchFirstPage: () -> Unit
): OrderListEmptyUiState {
    val errorText = if (isNetworkAvailable) {
        UiStringRes(R.string.error_refresh_orders)
    } else {
        UiStringRes(R.string.no_network_message)
    }
    return OrderListEmptyUiState.RefreshError(
            errorText,
            UiStringRes(R.string.retry),
            fetchFirstPage
    )
}

private fun createEmptyListUiState(
        orderListType: OrderListType
): OrderListEmptyUiState.EmptyList {
    return when (orderListType) {
        RECEIVING -> OrderListEmptyUiState.EmptyList(UiStringRes(R.string.orders_receiving_empty))
        DELIVERING -> OrderListEmptyUiState.EmptyList(UiStringRes(R.string.orders_delivering_empty))
        FINISHED -> OrderListEmptyUiState.EmptyList(UiStringRes(R.string.orders_finished_empty))
    }
}