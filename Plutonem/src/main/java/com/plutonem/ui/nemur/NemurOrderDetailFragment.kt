package com.plutonem.ui.nemur

import android.annotation.SuppressLint
import android.content.Context
import android.os.AsyncTask
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import com.plutonem.Plutonem
import com.plutonem.R
import com.plutonem.datasets.NemurOrderTable
import com.plutonem.models.NemurOrder
import com.plutonem.ui.main.PMainActivity
import com.plutonem.ui.nemur.NemurInterfaces.AutoHideToolbarListener
import com.plutonem.ui.nemur.NemurTypes.NemurOrderListType
import com.plutonem.ui.nemur.actions.NemurActions
import com.plutonem.ui.nemur.actions.NemurOrderActions
import com.plutonem.ui.nemur.video.VideoCustomController
import com.plutonem.ui.nemur.views.NemurIconView
import com.plutonem.ui.nemur.views.NemurPriceButton
import com.plutonem.utilities.AniUtils
import com.plutonem.utilities.PNSwipeToRefreshHelper.buildSwipeToRefreshHelper
import com.plutonem.widgets.PNScrollView.ScrollDirectionListener
import kohii.v1.core.*
import kohii.v1.exoplayer.DefaultControlDispatcher
import kohii.v1.exoplayer.Kohii
import kotlinx.android.synthetic.main.nemur_fragment_order_detail.*
import kotlinx.android.synthetic.main.nemur_fragment_order_detail.view.*
import kotlinx.android.synthetic.main.nemur_include_order_detail_footer.*
import org.greenrobot.eventbus.EventBus
import org.wordpress.android.util.AppLog
import org.wordpress.android.util.AppLog.T
import org.wordpress.android.util.NetworkUtils
import org.wordpress.android.util.ToastUtils
import org.wordpress.android.util.helpers.SwipeToRefreshHelper
import org.wordpress.android.util.widgets.CustomSwipeRefreshLayout

class NemurOrderDetailFragment : Fragment(),
        PMainActivity.OnActivityBackPressedListener,
        ScrollDirectionListener,
        Prioritized {
    private lateinit var kohii: Kohii
    private lateinit var manager: Manager
    private lateinit var playable: Playable

    private var orderId: Long = 0
    private var buyerId: Long = 0
    private var order: NemurOrder? = null
    private var orderVideo: NemurOrder? = null
    private var orderListType: NemurOrderListType = NemurTypes.DEFAULT_ORDER_LIST_TYPE

    private lateinit var swipeToRefreshHelper: SwipeToRefreshHelper

    //    private lateinit var scrollView: PNScrollView
    private lateinit var layoutFooter: ViewGroup
    private lateinit var nemurPriceButton: NemurPriceButton
//    private lateinit var playerView: PlayerView
//    private lateinit var layoutContainer:ViewGroup

    private var hasAlreadyRequestedOrder: Boolean = false

    private var toolbarHeight: Int = 0
    private var errorMessage: String? = null

    //    private var isToolbarShowing = true
    private var autoHideToolbarListener: AutoHideToolbarListener? = null

    /*
     * AsyncTask to retrieve this order from SQLite and display it
     */
    private var isOrderTaskRunning = false

    private var playback: Playback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity!!.application as Plutonem).component().inject(this)
    }

    override fun setArguments(args: Bundle?) {
        super.setArguments(args)
        if (args != null) {
            buyerId = args.getLong(NemurConstants.ARG_BUYER_ID)
            orderId = args.getLong(NemurConstants.ARG_ORDER_ID)
            if (args.containsKey(NemurConstants.ARG_ORDER_LIST_TYPE)) {
                this.orderListType = args.getSerializable(NemurConstants.ARG_ORDER_LIST_TYPE) as NemurOrderListType
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is AutoHideToolbarListener) {
            autoHideToolbarListener = context
        }
        toolbarHeight = context.resources.getDimensionPixelSize(R.dimen.wordpress_toolbar_height)
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.nemur_fragment_order_detail, container, false)

        val swipeRefreshLayout = view.findViewById<CustomSwipeRefreshLayout>(R.id.swipe_to_refresh)

        // this fragment hides/shows toolbar with scrolling, which messes up ptr animation position
        // so we have to set it manually
        val swipeToRefreshOffset = resources.getDimensionPixelSize(R.dimen.toolbar_content_offset)
        swipeRefreshLayout.setProgressViewOffset(false, 0, swipeToRefreshOffset)

        swipeToRefreshHelper = buildSwipeToRefreshHelper(
                swipeRefreshLayout
        ) {
            if (isAdded) {
                updateOrder()
            }
        }

//        scrollView = view.findViewById(R.id.scroll_view_nemur)
//        scrollView.setScrollDirectionListener(this)

        layoutFooter = view.findViewById(R.id.layout_order_detail_footer)

        // hide footer and scrollView until the post is loaded
        layoutFooter.visibility = View.INVISIBLE
//        scrollView.visibility = View.INVISIBLE

        nemurPriceButton = view.findViewById(R.id.price_button)

//        playerView = view.findViewById(R.id.playerView)

//        layoutContainer = view.findViewById(R.id.layout_order_detail_container)

//        layoutContainer.setOnClickListener {
//            ToastUtils.showToast(context, "Success");
//        }

        showOrder()

        return view
    }

    override fun onViewCreated(
            view: View,
            savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        kohii = Kohii[this]
        manager = kohii.register(this)
                .addBucket(layout_content)

        val pagePos = requireArguments().getInt(
                pageTagKey
        )

        if (order != null) {
            orderVideo = order
        } else {
            orderVideo = NemurOrderTable.getBuyerOrder(buyerId, orderId)
        }

        val videoTag = "PAGE::$pagePos::${orderVideo!!.featuredVideo}"
        val videoCustomController = VideoCustomController(manager, playerView)
        kohii.setUp(orderVideo!!.featuredVideo) {
                    tag = videoTag
                    delay = 500
                    repeatMode = Common.REPEAT_MODE_ALL
                    preload = true
                    controller = videoCustomController
//            controller = DefaultControlDispatcher(
//                    manager,
//                    playerView,
//                    kohiiCanStart = false, // set to false -> if user pause it, Kohii will not start it
//                    kohiiCanPause = true // set to true -> Kohii will pause it automatically
//            )
                }
                .bind(playerView) { playback = it }

        view.doOnLayout {
            // [1] Update resize mode based on Window size.
            playerContainer.also { ctn ->
                if (it.width * 9 >= it.height * 16) {
                    // if (it.width * it.height >= it.height * it.width) { // how about this?
                    ctn.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
                } else {
                    ctn.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                }
            }
        }

        val playerContainer = view.findViewById<AspectRatioFrameLayout>(R.id.playerContainer);
//
////        playerView.useController = true;
//        playerContainer.setOnClickListener {
//            manager.apply {
//                playable = findPlayableForContainer(playback?.container!!)!!.apply {
//                    if (this.isPlaying()) this.onPause()
//                    else this.onPlay()
//                }
//            }
//            playback.onPause()
//        }
        playerContainer.setOnClickListener(videoCustomController)
    }

    private fun hasOrder(): Boolean {
        return order != null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong(NemurConstants.ARG_BUYER_ID, buyerId)
        outState.putLong(NemurConstants.ARG_ORDER_ID, orderId)

        outState.putBoolean(NemurConstants.KEY_ALREADY_REQUESTED, hasAlreadyRequestedOrder)

        outState.putSerializable(NemurConstants.ARG_ORDER_LIST_TYPE,
                this.orderListType
        )

        if (!errorMessage.isNullOrEmpty()) {
            outState.putString(NemurConstants.KEY_ERROR_MESSAGE, errorMessage)
        }

        super.onSaveInstanceState(outState)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        restoreState(savedInstanceState)
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        savedInstanceState?.let {
            buyerId = it.getLong(NemurConstants.ARG_BUYER_ID)
            orderId = it.getLong(NemurConstants.ARG_ORDER_ID)
            hasAlreadyRequestedOrder = it.getBoolean(NemurConstants.KEY_ALREADY_REQUESTED)
            if (it.containsKey(NemurConstants.ARG_ORDER_LIST_TYPE)) {
                this.orderListType = it.getSerializable(NemurConstants.ARG_ORDER_LIST_TYPE) as NemurOrderListType
            }
            if (it.containsKey(NemurConstants.KEY_ERROR_MESSAGE)) {
                errorMessage = it.getString(NemurConstants.KEY_ERROR_MESSAGE)
            }
        }
    }

    /*
     * called by the activity when user hits the back button - returns true if the back button
     * is handled here and should be ignored by the activity
     */
    override fun onActivityBackPressed(): Boolean {
        return false
    }

    private fun initPriceView() {
        if (!canShowFooter()) {
            return
        }

        updatePriceView()
    }

    private fun updatePriceView() {
        if (!isAdded || !hasOrder()) {
            return
        }

        if (!canShowPriceView()) {
            nemurPriceButton.visibility = View.GONE
        } else {
            nemurPriceButton.visibility = View.VISIBLE
            nemurPriceButton.updateState(order!!.price)
        }
    }

    /*
     * get the latest version of this order
     */
    private fun updateOrder() {
        if (!hasOrder()) {
            setRefreshing(false)
            return
        }

        val resultListener = NemurActions.UpdateResultListener { result ->
            val order = this.order
            if (isAdded && order != null) {
                // if the order has changed, reload it from the db
                if (result.isNewOrChanged) {
                    this.order = NemurOrderTable.getBuyerOrder(order.buyerId, order.orderId)
                    refreshIconViews()
                }

                setRefreshing(false)
            }
        }
        NemurOrderActions.updateOrder(order!!, resultListener)
    }

    private fun refreshIconViews() {
        val order = this.order
        if (!isAdded || order == null || !canShowFooter()) {
            return
        }

        val viewBuy = view!!.findViewById<NemurIconView>(R.id.view_buy)
//        val playerView = view!!.findViewById<PlayerView>(R.id.playerView)

        if (canShowBuyView()) {
            viewBuy.setView()
            viewBuy.visibility = View.VISIBLE
            viewBuy.setOnClickListener {
                NemurActivityLauncher.showEditOrderView(
                        activity,
                        "official flagship store of plutonem",
                        order.title,
                        order.price,
                        order.itemDistributionMode

                )
            }

//            viewBuy.setOnClickListener {
//                manager.pause(manager.findPlayableForContainer(playerView)!!)
//            }

//            if (order.price.indexOf("$") == -1) {
//                viewBuy.setOnClickListener {
//                    NemurActivityLauncher.showEditOrderView(
//                            activity,
//                            "official flagship store of plutonem",
//                            order.title,
//                            order.price,
//                            "express delivery ￥0.00"
//
//                    )
//                }
//            } else {
//                viewBuy.setOnClickListener {
//                    NemurActivityLauncher.showEditOrderView(
//                            activity,
//                            "official flagship store of plutonem",
//                            order.title,
//                            order.price,
//                            "express delivery $0.00"
//
//                    )
//                }
//            }
        } else {
            viewBuy.visibility = View.INVISIBLE
            viewBuy.setOnClickListener(null)
        }
    }

    /*
     * called when the order doesn't exist in local db, need to get it from server
     */
    private fun requestOrder() {
        val progress = view!!.findViewById<ProgressBar>(R.id.progress_loading)
        progress.visibility = View.VISIBLE
        progress.bringToFront()

        val listener = object : NemurActions.OnRequestListener {
            override fun onSuccess() {
                hasAlreadyRequestedOrder = true
                if (isAdded) {
                    progress.visibility = View.GONE
                    showOrder()
                    EventBus.getDefault().post(NemurEvents.SingleOrderDownloaded())
                }
            }

            override fun onFailure(statusCode: Int) {
                hasAlreadyRequestedOrder = true
                if (isAdded) {
                    progress.visibility = View.GONE
                    onRequestFailure(statusCode)
                }
            }
        }

        NemurOrderActions.requestBuyerOrder(buyerId, orderId, listener)
    }

    private fun onRequestFailure(statusCode: Int) {
        val errMsgResId: Int
        if (!NetworkUtils.isNetworkAvailable(activity)) {
            errMsgResId = R.string.no_network_message
        } else {
            errMsgResId = when (statusCode) {
                401, 403 -> {
                    R.string.nemur_err_get_order_not_authorized
                }
                404 -> R.string.nemur_err_get_order_not_found
                else -> R.string.nemur_err_get_order_generic
            }
        }
        showError(getString(errMsgResId))
    }

    /*
     * shows an error message in the middle of the screen - used when requesting order fails
     */
    private fun showError(errorMessage: String?) {
        if (!isAdded) {
            return
        }

        val txtError = view!!.findViewById<TextView>(R.id.text_error)
        txtError.text = errorMessage
        if (errorMessage == null) {
            txtError.visibility = View.GONE
        } else if (txtError.visibility != View.VISIBLE) {
            AniUtils.fadeIn(txtError, AniUtils.Duration.MEDIUM)
        }
        this.errorMessage = errorMessage
    }

    private fun showOrder() {
        if (isOrderTaskRunning) {
            AppLog.w(T.NEMUR, "nemur order detail > show order task already running")
            return
        }

        ShowOrderTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    // TODO replace this inner async task with a coroutine
    @SuppressLint("StaticFieldLeak")
    private inner class ShowOrderTask : AsyncTask<Void, Void, Boolean>() {
        override fun onPreExecute() {
            isOrderTaskRunning = true
        }

        override fun onCancelled() {
            isOrderTaskRunning = false
        }

        override fun doInBackground(vararg params: Void): Boolean? {
            order = NemurOrderTable.getBuyerOrder(buyerId, orderId)
            if (order == null) {
                return false
            }

            return true
        }

        override fun onPostExecute(result: Boolean) {
            isOrderTaskRunning = false

            if (!isAdded) {
                return
            }

            // make sure options menu reflects whether we now have a order
            activity!!.invalidateOptionsMenu()

            if (!result) {
                // order couldn't be loaded which means it doesn't exist in db, so request it from
                // the server if it hasn't already been requested
                if (!hasAlreadyRequestedOrder) {
                    AppLog.i(T.NEMUR, "nemur order detail > order not found, requesting it")
                    requestOrder()
                } else if (!TextUtils.isEmpty(errorMessage)) {
                    // order has already been requested and failed, so restore previous error message
                    showError(errorMessage)
                }
            } else {
                showError(null)
            }

//            val headerView = view!!.findViewById<NemurOrderDetailHeaderView>(R.id.header_view)
            if (!canShowFooter()) {
                layoutFooter.visibility = View.GONE
            }

            // add padding to the playerContainer to make room for the top and bottom toolbars - this also
            // ensures the playerContainer matches the content so it doesn't disappear behind the toolbars
            val topPadding = if (autoHideToolbarListener != null) toolbarHeight else 0
            val bottomPadding = if (canShowFooter()) layoutFooter.height else 0
            playerContainer.setPadding(0, topPadding, 0, bottomPadding)

            // scrollView was hidden in onCreateView, show it now that we have the post
//            scrollView.visibility = View.VISIBLE

//            headerView.setOrder(order!!)

            if (canShowFooter() && layoutFooter.visibility != View.VISIBLE) {
                AniUtils.fadeIn(layoutFooter, AniUtils.Duration.LONG)
            }

            refreshIconViews()
            initPriceView()
        }
    }

    override fun onScrollUp(distanceY: Float) {
//        if (!isToolbarShowing && -distanceY >= MIN_SCROLL_DISTANCE_Y) {
//            showToolbar(true)
//            showFooter(true)
//        }
    }

    override fun onScrollDown(distanceY: Float) {
//        if (isToolbarShowing &&
//                distanceY >= MIN_SCROLL_DISTANCE_Y &&
//                scrollView.canScrollDown() &&
//                scrollView.canScrollUp() &&
//                scrollView.scrollY > toolbarHeight) {
//            showToolbar(false)
//            showFooter(false)
//        }
    }

    override fun onScrollCompleted() {
//        if (!isToolbarShowing && (!scrollView.canScrollDown() || !scrollView.canScrollUp())) {
//            showToolbar(true)
//            showFooter(true)
//        }
    }

    private fun showToolbar(show: Boolean) {
//        isToolbarShowing = show
//        if (autoHideToolbarListener != null) {
//            autoHideToolbarListener!!.onShowHideToolbar(show)
//        }
    }

    private fun showFooter(show: Boolean) {
//        if (isAdded && canShowFooter()) {
//            AniUtils.animateBottomBar(layoutFooter, show)
//        }
    }

    /*
     * can we show the footer bar which contains the price & buy view?
     */
    private fun canShowFooter(): Boolean {
        return canShowPriceView() || canShowBuyView()
    }

    private fun canShowPriceView(): Boolean {
        return hasOrder()
    }

    private fun canShowBuyView(): Boolean {
        return hasOrder()
    }

    private fun setRefreshing(refreshing: Boolean) {
        swipeToRefreshHelper.isRefreshing = refreshing
    }

    companion object {
        private const val pageTagKey = "nemur:order:pager:tag"

        // min scroll distance before toggling toolbar
//        private const val MIN_SCROLL_DISTANCE_Y = 10f

        fun newInstance(buyerId: Long, orderId: Long): NemurOrderDetailFragment {
            return newInstance(buyerId, orderId, null, 0)
        }

        fun newInstance(
                buyerId: Long,
                orderId: Long,
                orderListType: NemurOrderListType?,
                position: Int
        ): NemurOrderDetailFragment {
            AppLog.d(T.NEMUR, "nemur order detail > newInstance")

            val args = Bundle()
            args.putLong(NemurConstants.ARG_BUYER_ID, buyerId)
            args.putLong(NemurConstants.ARG_ORDER_ID, orderId)
            args.putInt(pageTagKey, position)
            if (orderListType != null) {
                args.putSerializable(NemurConstants.ARG_ORDER_LIST_TYPE, orderListType)
            }

            val fragment = NemurOrderDetailFragment()
            fragment.arguments = args

            return fragment
        }
    }
}