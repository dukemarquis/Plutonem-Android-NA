package com.plutonem.ui.products

import androidx.lifecycle.*
import com.plutonem.R
import com.plutonem.android.fluxc.Dispatcher
import com.plutonem.android.fluxc.model.BuyerModel
import com.plutonem.android.fluxc.store.OrderStore
import com.plutonem.modules.BG_THREAD
import com.plutonem.ui.pages.SnackbarMessageHolder
import com.plutonem.ui.prefs.AppPrefsWrapper
import com.plutonem.ui.products.OrderListType.*
import com.plutonem.utilities.NetworkUtilsWrapper
import com.plutonem.viewmodels.SingleLiveEvent
import com.plutonem.viewmodels.helpers.ToastMessageHolder
import com.plutonem.viewmodels.orders.OrderFetcher
import com.plutonem.viewmodels.orders.OrderListViewModelConnector
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.wordpress.android.util.ToastUtils
import javax.inject.Inject
import javax.inject.Named
import kotlin.coroutines.CoroutineContext

val ORDER_LIST_PAGES = listOf(DELIVERING, RECEIVING, FINISHED)

class OrderListMainViewModel @Inject constructor(
        private val dispatcher: Dispatcher,
        private val prefs: AppPrefsWrapper,
        @Named(BG_THREAD) private val bgDispatcher: CoroutineDispatcher
) : ViewModel(), LifecycleOwner, CoroutineScope {
    private val lifecycleRegistry = LifecycleRegistry(this)
    override fun getLifecycle(): Lifecycle = lifecycleRegistry

    private val scrollToTargetPostJob: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = bgDispatcher + scrollToTargetPostJob

    private lateinit var buyer: BuyerModel

    private val _viewState = MutableLiveData<OrderListMainViewState>()
    val viewState: LiveData<OrderListMainViewState> = _viewState

    private val _accountSelectionUpdated = MutableLiveData<AccountFilterSelection>()
    val accountSelectionUpdated = _accountSelectionUpdated

    private val _viewLayoutType = MutableLiveData<OrderListViewLayoutType>()
    val viewLayoutType: LiveData<OrderListViewLayoutType> = _viewLayoutType

    private val orderFetcher by lazy {
        OrderFetcher(lifecycle, dispatcher)
    }

    /**
     * This behavior is consistent with Calypso as of 11/4/2019.
     */
    private val isFilteringByAccountSupported: Boolean by lazy {
        buyer.isPN
    }

    init {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun start(buyer: BuyerModel) {
        this.buyer = buyer

        setUserPreferredViewLayoutType()

        val accountFilterSelection: AccountFilterSelection = if (isFilteringByAccountSupported) {
            prefs.orderListAccountSelection
        } else {
            AccountFilterSelection.EVERYONE
        }

        _accountSelectionUpdated.value = accountFilterSelection
        _viewState.value = OrderListMainViewState(
                isAccountFilterVisible = isFilteringByAccountSupported,
                accountFilterSelection = accountFilterSelection,
                accountFilterItems = getAccountFilterItems(accountFilterSelection)
        )

        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    override fun onCleared() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onCleared()
    }

    /*
    * FUTURE_REFACTOR: We shouldn't need to pass the AccountFilterSelection to fragments and get it back, we have that
    * info already
    */
    fun getOrderListViewModelConnector(
            orderListType: OrderListType
    ): OrderListViewModelConnector {
        return OrderListViewModelConnector(
                buyer = buyer,
                orderListType = orderListType,
                orderFetcher = orderFetcher
        )
    }

    fun onTabChanged() {
        updateViewStateTriggerPagerChange()
    }

    /**
     * Only the non-null variables will be changed in the current state
     */
    private fun updateViewStateTriggerPagerChange(
            isAccountFilterVisible: Boolean? = null,
            accountFilterSelection: AccountFilterSelection? = null,
            accountFilterItems: List<AccountFilterListItemUIState>? = null
    ) {
        val currentState = requireNotNull(viewState.value) {
            "updateViewStateTriggerPagerChange should not be called before the initial state is set"
        }

        _viewState.value = OrderListMainViewState(
                isAccountFilterVisible ?: currentState.isAccountFilterVisible,
                accountFilterSelection ?: currentState.accountFilterSelection,
                accountFilterItems ?: currentState.accountFilterItems
        )

        if (accountFilterSelection != null && currentState.accountFilterSelection != accountFilterSelection) {
            _accountSelectionUpdated.value = accountFilterSelection
        }
    }

    private fun setViewLayoutAndIcon(layout: OrderListViewLayoutType, storeIntoPreferences: Boolean = true) {
        _viewLayoutType.value = layout
        if (storeIntoPreferences) {
            prefs.orderListViewLayoutType = layout
        }
    }

    private fun setUserPreferredViewLayoutType() {
        val savedLayoutType = prefs.orderListViewLayoutType
        setViewLayoutAndIcon(savedLayoutType, false)
    }
}