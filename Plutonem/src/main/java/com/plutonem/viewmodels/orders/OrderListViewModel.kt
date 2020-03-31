package com.plutonem.viewmodels.orders

import android.annotation.SuppressLint
import androidx.lifecycle.*
import androidx.paging.PagedList
import com.plutonem.android.fluxc.Dispatcher
import com.plutonem.android.fluxc.model.OrderModel
import com.plutonem.android.fluxc.model.list.AccountFilter
import com.plutonem.android.fluxc.model.list.AccountFilter.Everyone
import com.plutonem.android.fluxc.model.list.OrderListDescriptor
import com.plutonem.android.fluxc.model.list.OrderListDescriptor.OrderListDescriptorForRestBuyer
import com.plutonem.android.fluxc.model.list.PagedListWrapper
import com.plutonem.android.fluxc.store.ListStore
import com.plutonem.android.fluxc.store.OrderStore
import com.plutonem.modules.BG_THREAD
import com.plutonem.modules.UI_THREAD
import com.plutonem.ui.products.AccountFilterSelection
import com.plutonem.ui.products.AccountFilterSelection.EVERYONE
import com.plutonem.utilities.NetworkUtilsWrapper
import com.plutonem.utilities.ThrottleLiveData
import com.plutonem.viewmodels.ScopedViewModel
import com.plutonem.viewmodels.helpers.ConnectionStatus
import com.plutonem.viewmodels.orders.OrderListEmptyUiState.RefreshError
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject
import javax.inject.Named
import kotlin.properties.Delegates

typealias PagedOrderList = PagedList<OrderListItemType>

private const val EMPTY_VIEW_THROTTLE = 250L

@SuppressLint("UseSparseArrays")
class OrderListViewModel @Inject constructor(
        private val dispatcher: Dispatcher,
        private val listStore: ListStore,
        private val orderStore: OrderStore,
        private val listItemUiStateHelper: OrderListItemUiStateHelper,
        private val networkUtilsWrapper: NetworkUtilsWrapper,
        @Named(UI_THREAD) private val uiDispatcher: CoroutineDispatcher,
        @Named(BG_THREAD) private val bgDispatcher: CoroutineDispatcher,
        connectionStatus: LiveData<ConnectionStatus>
) : ScopedViewModel(uiDispatcher), LifecycleOwner {
    private var isStarted: Boolean = false
    private lateinit var connector: OrderListViewModelConnector

    private var photonWidth by Delegates.notNull<Int>()
    private var photonHeight by Delegates.notNull<Int>()

    private val dataSource: OrderListItemDataSource by lazy {
        OrderListItemDataSource(
                dispatcher = dispatcher,
                orderStore = orderStore,
                orderFetcher = connector.orderFetcher,
                transform = this::transformOrderModelToOrderListItemUiState
        )
    }

    private var pagedListWrapper: PagedListWrapper<OrderListItemType>? = null

    private val _pagedListData = MediatorLiveData<PagedOrderList>()
    val pagedListData: LiveData<PagedOrderList> = _pagedListData

    private val _emptyViewState = ThrottleLiveData<OrderListEmptyUiState>(
            offset = EMPTY_VIEW_THROTTLE,
            coroutineScope = this,
            mainDispatcher = uiDispatcher,
            backgroundDispatcher = bgDispatcher
    )
    val emptyViewState: LiveData<OrderListEmptyUiState> = _emptyViewState

    private val _isLoadingMore = MediatorLiveData<Boolean>()
    val isLoadingMore: LiveData<Boolean> = _isLoadingMore

    private val _isFetchingFirstPage = MediatorLiveData<Boolean>()
    val isFetchingFirstPage: LiveData<Boolean> = _isFetchingFirstPage

    private lateinit var accountFilterSelection: AccountFilterSelection

    private val lifecycleRegistry = LifecycleRegistry(this)
    override fun getLifecycle(): Lifecycle = lifecycleRegistry

    fun start(
            orderListViewModelConnector: OrderListViewModelConnector,
            value: AccountFilterSelection,
            photonWidth: Int,
            photonHeight: Int
    ) {
        if (isStarted) {
            return
        }
        this.photonHeight = photonHeight
        this.photonWidth = photonWidth
        connector = orderListViewModelConnector

        isStarted = true
        lifecycleRegistry.currentState = Lifecycle.State.STARTED

        this.accountFilterSelection = value
        initList(dataSource, lifecycle)
    }

    private fun initList(dataSource: OrderListItemDataSource, lifecycle: Lifecycle) {
        val listDescriptor: OrderListDescriptor = initListDescriptor()

        clearLiveDataSources()

        val pagedListWrapper = listStore.getList(listDescriptor, dataSource, lifecycle)

        listenToEmptyViewStateLiveData(pagedListWrapper)

        _pagedListData.addSource(pagedListWrapper.data) { pagedOrderList ->
            pagedOrderList?.let {
                if (isSearchResultDeliverable()) {
                    _pagedListData.value = it
                }
            }
        }
        _isFetchingFirstPage.addSource(pagedListWrapper.isFetchingFirstPage) {
            _isFetchingFirstPage.value = it == true
        }
        _isLoadingMore.addSource(pagedListWrapper.isLoadingMore) {
            _isLoadingMore.value = it
        }

        this.pagedListWrapper = pagedListWrapper
        fetchFirstPage()
    }

    private fun clearLiveDataSources() {
        pagedListWrapper?.let {
            _pagedListData.removeSource(it.data)
            _emptyViewState.removeSource(pagedListData)
            _emptyViewState.removeSource(it.isEmpty)
            _emptyViewState.removeSource(it.isFetchingFirstPage)
            _emptyViewState.removeSource(it.listError)
            _isFetchingFirstPage.removeSource(it.isFetchingFirstPage)
            _isLoadingMore.removeSource(it.isLoadingMore)
        }
    }

    private fun initListDescriptor(): OrderListDescriptor {
        val account: AccountFilter = when (accountFilterSelection) {
            EVERYONE -> Everyone
        }

        return OrderListDescriptorForRestBuyer(
                buyer = connector.buyer,
                statusList = connector.orderListType.orderStatuses,
                account = account
        )
    }

    private fun listenToEmptyViewStateLiveData(pagedListWrapper: PagedListWrapper<OrderListItemType>) {
        val update = {
            createEmptyUiState(
                    orderListType = connector.orderListType,
                    isNetworkAvailable = networkUtilsWrapper.isNetworkAvailable(),
                    isLoadingData = pagedListWrapper.isFetchingFirstPage.value ?: false ||
                            pagedListWrapper.data.value == null,
                    isListEmpty = pagedListWrapper.isEmpty.value ?: true,
                    error = pagedListWrapper.listError.value,
                    fetchFirstPage = this::fetchFirstPage
            )
        }

        _emptyViewState.addSource(pagedListWrapper.isEmpty) { _emptyViewState.postValue(update()) }
        _emptyViewState.addSource(pagedListWrapper.isFetchingFirstPage) { _emptyViewState.postValue(update()) }
        _emptyViewState.addSource(pagedListWrapper.listError) { _emptyViewState.postValue(update()) }
    }

    // used to filter out dataset changes that might trigger empty view when performing search
    private fun isSearchResultDeliverable(): Boolean {
        return true
    }

    init {
        connectionStatus.observe(this, Observer {
            retryOnConnectionAvailableAfterRefreshError()
        })
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onCleared() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onCleared()
    }

    // Public Methods

    fun swipeToRefresh() {
        fetchFirstPage()
    }

    // Utils

    private fun fetchFirstPage() {
        pagedListWrapper?.fetchFirstPage()
    }

    private fun transformOrderModelToOrderListItemUiState(order: OrderModel) =
            listItemUiStateHelper.createOrderListItemUiState(
                    order = order
            )


    private fun retryOnConnectionAvailableAfterRefreshError() {
        val connectionAvailableAfterRefreshError = networkUtilsWrapper.isNetworkAvailable() &&
                emptyViewState.value is RefreshError

        if (connectionAvailableAfterRefreshError) {
            fetchFirstPage()
        }
    }

    fun updateAccountFilterIfNotSearch(accountFilterSelection: AccountFilterSelection): Boolean {
        return false
    }
}