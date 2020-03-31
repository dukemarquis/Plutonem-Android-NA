package com.plutonem.ui.products

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.plutonem.Plutonem
import com.plutonem.R
import com.plutonem.android.fluxc.model.BuyerModel
import com.plutonem.ui.ActionableEmptyView
import com.plutonem.ui.products.OrderListViewLayoutType.STANDARD
import com.plutonem.ui.products.adapters.OrderListAdapter
import com.plutonem.ui.utils.UiHelpers
import com.plutonem.utilities.PNSwipeToRefreshHelper.buildSwipeToRefreshHelper
import com.plutonem.utilities.image.ImageManager
import com.plutonem.viewmodels.orders.OrderListEmptyUiState
import com.plutonem.viewmodels.orders.OrderListViewModel
import com.plutonem.viewmodels.orders.PagedOrderList
import com.plutonem.widgets.RecyclerItemDecoration
import org.wordpress.android.util.DisplayUtils
import org.wordpress.android.util.NetworkUtils
import org.wordpress.android.util.ToastUtils
import org.wordpress.android.util.helpers.SwipeToRefreshHelper
import org.wordpress.android.util.widgets.CustomSwipeRefreshLayout
import javax.inject.Inject

private const val EXTRA_ORDER_LIST_TYPE = "order_list_type"
private const val MAX_INDEX_FOR_VISIBLE_ITEM_TO_KEEP_SCROLL_POSITION = 2

class OrderListFragment : Fragment() {
    @Inject internal lateinit var viewModelFactory: ViewModelProvider.Factory
    @Inject internal lateinit var uiHelpers: UiHelpers
    private lateinit var viewModel: OrderListViewModel
    private lateinit var mainViewModel: OrderListMainViewModel

    private var swipeToRefreshHelper: SwipeToRefreshHelper? = null

    private var swipeRefreshLayout: CustomSwipeRefreshLayout? = null
    private var recyclerView: RecyclerView? = null
    private var actionableEmptyView: ActionableEmptyView? = null
    private var progressLoadMore: ProgressBar? = null

    private lateinit var itemDecorationStandardLayout: RecyclerItemDecoration

    private lateinit var orderListType: OrderListType

    private lateinit var nonNullActivity: FragmentActivity

    private val orderListAdapter: OrderListAdapter by lazy {
        OrderListAdapter(
                context = nonNullActivity,
                uiHelpers = uiHelpers
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nonNullActivity = checkNotNull(activity)
        (nonNullActivity.application as Plutonem).component().inject(this)

        val nonNullIntent = checkNotNull(nonNullActivity.intent)
        val buyer: BuyerModel? = nonNullIntent.getSerializableExtra(Plutonem.BUYER) as BuyerModel?

        if (buyer == null) {
            ToastUtils.showToast(nonNullActivity, R.string.buyer_not_found, ToastUtils.Duration.SHORT)
            nonNullActivity.finish()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        orderListType = requireNotNull(arguments).getSerializable(EXTRA_ORDER_LIST_TYPE) as OrderListType

        mainViewModel = ViewModelProviders.of(nonNullActivity, viewModelFactory)
                .get(OrderListMainViewModel::class.java)

        mainViewModel.viewLayoutType.observe(viewLifecycleOwner, Observer { optionalLayoutType ->
            optionalLayoutType?.let { layoutType ->
                when (layoutType) {
                    STANDARD -> {
                        recyclerView?.addItemDecoration(itemDecorationStandardLayout)
                    }
                }

                if (orderListAdapter.updateItemLayoutType(layoutType)) {
                    recyclerView?.scrollToPosition(0)
                }
            }
        })

        mainViewModel.accountSelectionUpdated.observe(viewLifecycleOwner, Observer {
            if (it != null) {
                if (viewModel.updateAccountFilterIfNotSearch(it)) {
                    recyclerView?.scrollToPosition(0)
                }
            }
        })

        actionableEmptyView?.updateLayoutForSearch(false, 0)

        val orderListViewModelConnector = mainViewModel.getOrderListViewModelConnector(orderListType)

        viewModel = ViewModelProviders.of(this, viewModelFactory).get(OrderListViewModel::class.java)

        val displayWidth = DisplayUtils.getDisplayPixelWidth(context)
        val contentSpacing = nonNullActivity.resources.getDimensionPixelSize(R.dimen.wordpress_content_margin)

        // since the MainViewModel has been already started, we need to manually update the authorFilterSelection value
        viewModel.start(
                orderListViewModelConnector,
                mainViewModel.accountSelectionUpdated.value!!,
                photonWidth = displayWidth - contentSpacing * 2,
                photonHeight = nonNullActivity.resources.getDimensionPixelSize(R.dimen.wordpress_reader_featured_image_height)
        )

        initObservers()
    }

    private fun initObservers() {
        viewModel.emptyViewState.observe(viewLifecycleOwner, Observer {
            it?.let { emptyViewState -> updateEmptyViewForState(emptyViewState) }
        })

        viewModel.isFetchingFirstPage.observe(viewLifecycleOwner, Observer {
            swipeRefreshLayout?.isRefreshing = it == true
        })

        viewModel.pagedListData.observe(viewLifecycleOwner, Observer {
            it?.let { pagedListData -> updatePagedListData(pagedListData) }
        })

        viewModel.isLoadingMore.observe(viewLifecycleOwner, Observer {
            progressLoadMore?.visibility = if (it == true) View.VISIBLE else View.GONE
        })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.order_list_fragment, container, false)

        swipeRefreshLayout = view.findViewById(R.id.ptr_layout)
        recyclerView = view.findViewById(R.id.recycler_view)
        progressLoadMore = view.findViewById(R.id.progress)
        actionableEmptyView = view.findViewById(R.id.actionable_empty_view)

        val context = nonNullActivity
        itemDecorationStandardLayout = RecyclerItemDecoration(
                0,
                context.resources.getDimensionPixelSize(R.dimen.margin_medium)
        )
        recyclerView?.layoutManager = LinearLayoutManager(context)
        recyclerView?.adapter = orderListAdapter

        swipeToRefreshHelper = buildSwipeToRefreshHelper(swipeRefreshLayout) {
            if (!NetworkUtils.isNetworkAvailable(nonNullActivity)) {
                swipeRefreshLayout?.isRefreshing = false
            } else {
                viewModel.swipeToRefresh()
            }
        }
        return view
    }

    /**
     * Updates the data for the adapter while retaining visible item in certain cases.
     *
     * PagedList tries to keep the visible item by adding new items outside of the screen while modifying the scroll
     * position. In most cases, this works out great because it doesn't interrupt to the user. However, after a new
     * item is inserted at the top while the list is showing the very first items, it feels very weird to not have the
     * inserted item shown. For example, if a new draft is added there is no indication of it except for the flash
     * of the scroll bar which is not noticeable unless user is paying attention to it.
     *
     * In these cases, we try to keep the scroll position the same instead of keeping the visible item the same by
     * first saving the state and re-applying it after the data updates are completed. Since `PagedListAdapter` uses
     * bg thread to calculate the changes, we need to post the change in the bg thread as well so that it'll be applied
     * after changes are reflected.
     */
    private fun updatePagedListData(pagedListData: PagedOrderList) {
        val recyclerViewState = recyclerView?.layoutManager?.onSaveInstanceState()
        orderListAdapter.submitList(pagedListData)
        recyclerView?.post {
            (recyclerView?.layoutManager as? LinearLayoutManager)?.let { layoutManager ->
                if (layoutManager.findFirstVisibleItemPosition() < MAX_INDEX_FOR_VISIBLE_ITEM_TO_KEEP_SCROLL_POSITION) {
                    layoutManager.onRestoreInstanceState(recyclerViewState)
                }
            }
        }
    }


    private fun updateEmptyViewForState(state: OrderListEmptyUiState) {
        actionableEmptyView?.let { emptyView ->
            if (state.emptyViewVisible) {
                emptyView.visibility = View.VISIBLE
                uiHelpers.setTextOrHide(emptyView.title, state.title)
                uiHelpers.setImageOrHide(emptyView.image, state.imgResId)
            } else {
                emptyView.visibility = View.GONE
            }
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(
                buyer: BuyerModel,
                orderListType: OrderListType
        ): OrderListFragment {
            val fragment = OrderListFragment()
            val bundle = Bundle()
            bundle.putSerializable(Plutonem.BUYER, buyer)
            bundle.putSerializable(EXTRA_ORDER_LIST_TYPE, orderListType)
            fragment.arguments = bundle
            return fragment
        }
    }
}