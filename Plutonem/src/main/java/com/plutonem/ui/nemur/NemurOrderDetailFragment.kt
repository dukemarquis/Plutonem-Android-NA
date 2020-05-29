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
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.plutonem.Plutonem
import com.plutonem.R
import com.plutonem.databinding.NemurFragmentOrderDetailBinding
import com.plutonem.datasets.NemurOrderTable
import com.plutonem.models.NemurOrder
import com.plutonem.ui.main.PMainActivity
import com.plutonem.ui.nemur.NemurInterfaces.AutoHideToolbarListener
import com.plutonem.ui.nemur.NemurInterfaces.ChatInterfaceListener
import com.plutonem.ui.nemur.NemurTypes.NemurOrderListType
import com.plutonem.ui.nemur.actions.NemurActions
import com.plutonem.ui.nemur.actions.NemurOrderActions
import com.plutonem.ui.nemur.video.NemurItemVideoController
import com.plutonem.ui.nemur.views.NemurIconView
import com.plutonem.ui.nemur.views.NemurPriceButton
import com.plutonem.utilities.AniUtils
import com.plutonem.utilities.PNSwipeToRefreshHelper.buildSwipeToRefreshHelper
import com.plutonem.widgets.PNScrollView
import com.plutonem.widgets.PNScrollView.*
import kohii.v1.core.*
import kohii.v1.exoplayer.Kohii
import kotlinx.android.synthetic.main.nemur_fragment_order_detail.*
import org.greenrobot.eventbus.EventBus
import org.wordpress.android.util.AppLog
import org.wordpress.android.util.AppLog.T
import org.wordpress.android.util.NetworkUtils
import org.wordpress.android.util.helpers.SwipeToRefreshHelper
import org.wordpress.android.util.widgets.CustomSwipeRefreshLayout

class NemurOrderDetailFragment : Fragment(),
        PMainActivity.OnActivityBackPressedListener,
        ScrollDirectionListener,
        Prioritized,
        Playback.ArtworkHintListener,
        Presenter {
    private lateinit var kohii: Kohii
    private lateinit var manager: Manager
    private lateinit var playable: Playable

    private var orderId: Long = 0
    private var buyerId: Long = 0
    private var order: NemurOrder? = null
//    private var orderVideo: NemurOrder? = null
    private var orderListType: NemurOrderListType = NemurTypes.DEFAULT_ORDER_LIST_TYPE

    lateinit var binding: NemurFragmentOrderDetailBinding
    private lateinit var swipeToRefreshHelper: SwipeToRefreshHelper
    private lateinit var scrollView: PNScrollView
    private lateinit var layoutFooter: ViewGroup
    private lateinit var nemurPriceButton: NemurPriceButton

    private var hasAlreadyRequestedOrder: Boolean = false

    private var toolbarHeight: Int = 0
    private var errorMessage: String? = null

    private var isToolbarShowing = true
    private var autoHideToolbarListener: AutoHideToolbarListener? = null

    private var chatInterfaceListener: ChatInterfaceListener? = null

    /*
     * AsyncTask to retrieve this order from SQLite and display it
     */
    private var isOrderTaskRunning = false

    // Kohii Video Specification
    private var playback: Playback? = null
    private var videoItem: NemurOrder? = null
//    private lateinit var kohiiExoPlay: ImageButton
//    private lateinit var shortVideoPlayerContainer: AspectRatioFrameLayout

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
        if (context is ChatInterfaceListener) {
            chatInterfaceListener = context
        }
        toolbarHeight = context.resources.getDimensionPixelSize(R.dimen.toolbar_height)
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.nemur_fragment_order_detail, container, false)

        binding = DataBindingUtil.inflate(
                inflater,
                R.layout.nemur_fragment_order_detail,
                container,
                false
        ) as NemurFragmentOrderDetailBinding

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

        scrollView = view.findViewById( R.id.scroll_view_nemur )
        scrollView.setScrollDirectionListener( this )

        layoutFooter = view.findViewById(R.id.layout_order_detail_footer)

        // hide footer and scrollView until the item is loaded
        layoutFooter.visibility = View.INVISIBLE
        scrollView.visibility = View.INVISIBLE

        nemurPriceButton = view.findViewById(R.id.price_button)

//        kohiiExoPlay = view.findViewById(R.id.kohii_exo_play)
//        exoPlay.setImageDrawable(resources.getDrawable(R.id.animated_vector, null))

//        val animatedVector = resources.getDrawable(
//                R.drawable.animator_vector_drawable,
//                null
//        ) as AnimatedVectorDrawable
//
//        exoPlay.setImageDrawable(animatedVector)

        showOrder()

        return view
    }

    override fun onViewCreated(
            view: View,
            savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val kohii = Kohii[this]
        val manager = kohii.register( this )
                .addBucket( scroll_view_nemur )

        videoItem = if (order != null) {
            order
        } else {
            NemurOrderTable.getBuyerOrder(buyerId, orderId)
        }

        val featuredVideoTag = "${videoItem?.title}::${videoItem?.itemDescriptiveVideoMain}"

        val featuredVideoController = NemurItemVideoController( manager, featured_player_view, featured_player_controller )

        kohii.setUp( videoItem!!.itemDescriptiveVideoMain ) {
            tag                 = featuredVideoTag
            delay               = 500
            repeatMode          = Common.REPEAT_MODE_ALL
            preload             = true
            controller          = featuredVideoController
            artworkHintListener = this@NemurOrderDetailFragment
        }
                .bind( featured_player_view )

        featured_player_container.setOnClickListener( featuredVideoController )
        featured_player_controller.setOnClickListener( featuredVideoController )

        val affiliatedVideoTag = "${videoItem?.title}::${videoItem?.itemDescriptiveVideoAffiliated}"

        val affiliatedVideoController = NemurItemVideoController( manager, affiliated_player_view, affiliated_player_controller )

        kohii.setUp( videoItem!!.itemDescriptiveVideoAffiliated ) {
            tag                 = affiliatedVideoTag
            delay               = 500
            repeatMode          = Common.REPEAT_MODE_ALL
            preload             = true
            controller          = affiliatedVideoController
            artworkHintListener = this@NemurOrderDetailFragment
        }
                .bind( affiliated_player_view )

        affiliated_player_container.setOnClickListener( affiliatedVideoController )
        affiliated_player_controller.setOnClickListener( affiliatedVideoController )

//        binding.lifecycleOwner = viewLifecycleOwner

//        kohii = Kohii[this]
//        manager = kohii.register(this)
//                .addBucket(layout_content)
//
//        val pagePos = requireArguments().getInt(
//                pageTagKey
//        )

//        if (order != null) {
//            orderVideo = order
//        } else {
//            orderVideo = NemurOrderTable.getBuyerOrder(buyerId, orderId)
//        }

//        val featuredVideoTag = "PAGE::$pagePos::${orderVideo!!.itemDescriptiveVideoMain}"
//        val videoController = VideoCustomController(manager, nemur_featured_video_view)

//        kohii.setUp(orderVideo!!.itemDescriptiveVideoMain) {
//                    tag = featuredVideoTag
//                    delay = 500
//                    preload = true
//                    repeatMode = Common.REPEAT_MODE_ALL
//                    controller = videoController
//                    artworkHintListener = this@NemurOrderDetailFragment
//        }
//            .bind(nemur_featured_video_view) { playback = it }
//
//        view.doOnLayout {
//            // [1] Update resize mode based on Window size.
//            nemur_featured_video_container.also { ctn ->
//                if (it.width * 9 >= it.height * 16) {
//                    // if (it.width * it.height >= it.height * it.width) { // how about this?
//                    ctn.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
//                } else {
//                    ctn.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
//                }
//            }
//        }

//        nemur_featured_video_container.setOnClickListener(videoController)

//        if ( orderVideo!!.hasItemDescriptiveVideoMain() ) {
//            nemur_affiliated_video_container.visibility = View.VISIBLE
//        }
//
//        if ( nemur_affiliated_video_container.visibility == View.VISIBLE ) {
////            val affiliatedVideoTag = "PAGE::$pagePos::${orderVideo!!.itemDescriptiveVideoMain}"
////            val affiliatedVideoController = VideoCustomController(manager, nemur_affiliated_video_view)
//
//            kohii.setUp(orderVideo!!.itemDescriptiveVideoMain) {
////                tag = affiliatedVideoTag
//                delay = 500
//                preload = true
//                repeatMode = Common.REPEAT_MODE_ALL
////                controller = affiliatedVideoController
//                artworkHintListener = this@NemurOrderDetailFragment
//            }
//                .bind(nemur_affiliated_video_view) { playback = it }
//
//            view.doOnLayout {
//                // [1] Update resize mode based on Window size.
//                nemur_affiliated_video_container.also { ctn ->
//                    if (it.width * 9 >= it.height * 16) {
//                        // if (it.width * it.height >= it.height * it.width) { // how about this?
//                        ctn.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
//                    } else {
//                        ctn.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
//                    }
//                }
//            }
//
////            nemur_affiliated_video_container.setOnClickListener(videoController)
//        }

//        exoPlay.setOnClickListener(videoController)
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

        val actionBuy = view!!.findViewById<NemurIconView>(R.id.view_buy)
        val actionChat = view!!.findViewById<NemurIconView>(R.id.view_chat)

        if (canShowBuyView()) {
            actionBuy.setAction(0)
            actionBuy.visibility = View.VISIBLE
            actionBuy.setOnClickListener {
                NemurActivityLauncher.showEditOrderView(
                        activity,
                        "official flagship store of plutonem",
                        order.title,
                        order.price,
                        order.itemDistributionMode

                )
            }
        } else {
            actionBuy.visibility = View.INVISIBLE
            actionBuy.setOnClickListener(null)
        }

        if (canShowChatView()) {
            actionChat.setAction(1)
            actionChat.visibility = View.VISIBLE
            actionChat.setOnClickListener {
                if (chatInterfaceListener != null) {
                    chatInterfaceListener!!.onShowChat();
                }
            }
        } else {
            actionChat.visibility = View.INVISIBLE
            actionChat.setOnClickListener(null)
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
            // ensures the scrollbar matches the content so it doesn't disappear behind the toolbars
            val topPadding = if (autoHideToolbarListener != null) toolbarHeight else 0
            val bottomPadding = if (canShowFooter()) layoutFooter.height else 0
//            nemur_featured_video_container.setPadding( 0, topPadding, 0, bottomPadding );
            scrollView.setPadding(0, topPadding, 0, bottomPadding)

            // scrollView was hidden in onCreateView, show it now that we have the post
            scrollView.visibility = View.VISIBLE

//            headerView.setOrder(order!!)

            if (canShowFooter() && layoutFooter.visibility != View.VISIBLE) {
                AniUtils.fadeIn(layoutFooter, AniUtils.Duration.LONG)
            }

            refreshIconViews()
            initPriceView()
        }
    }

    override fun onScrollUp(distanceY: Float) {
        if (!isToolbarShowing && -distanceY >= MIN_SCROLL_DISTANCE_Y) {
            showToolbar(true)
            showFooter(true)
        }
    }

    override fun onScrollDown(distanceY: Float) {
        if (isToolbarShowing &&
                distanceY >= MIN_SCROLL_DISTANCE_Y &&
                scrollView.canScrollDown() &&
                scrollView.canScrollUp() &&
                scrollView.scrollY > toolbarHeight) {
            showToolbar(false)
            showFooter(false)
        }
    }

    override fun onScrollCompleted() {
        if (!isToolbarShowing && (!scrollView.canScrollDown() || !scrollView.canScrollUp())) {
            showToolbar(true)
            showFooter(true)
        }
    }

    private fun showToolbar(show: Boolean) {
        isToolbarShowing = show
        if (autoHideToolbarListener != null) {
            autoHideToolbarListener!!.onShowHideToolbar(show)
        }
    }

    private fun showFooter(show: Boolean) {
        if (isAdded && canShowFooter()) {
            AniUtils.animateBottomBar(layoutFooter, show)
        }
    }

    /*
     * can we show the footer bar which contains the price & buy view?
     */
    private fun canShowFooter(): Boolean {
        return canShowPriceView() || canShowBuyView() || canShowChatView()
    }

    private fun canShowBuyView(): Boolean {
        return hasOrder()
    }

    private fun canShowChatView(): Boolean {
        return hasOrder()
    }

    private fun canShowPriceView(): Boolean {
        return hasOrder()
    }

    private fun setRefreshing(refreshing: Boolean) {
        swipeToRefreshHelper.isRefreshing = refreshing
    }

    override fun onArtworkHint(
            shouldShow: Boolean,
            position: Long,
            state: Int
    ) {
//        exoPlay.isVisible = shouldShow && state == Common.STATE_READY
//        if (exoPlay.visibility == View.VISIBLE) {
//            val animatable = exoPlay.drawable as android.graphics.drawable.Animatable
//            animatable.start()
//        }
//        if (shouldShow && state == Common.STATE_READY) {
//            exoPlay.apply {
//                visibility = View.VISIBLE
//
//                val animatable = drawable as android.graphics.drawable.Animatable
//                animatable.start()
//            }
//        }
    }

    override fun onStart() {
        super.onStart()
        binding.presenter = this
    }

    override fun onStop() {
        super.onStop()
        binding.presenter = null
    }

    override fun requireProvider(): Kohii {
        return Kohii[this]
    }

    companion object {
        private const val pageTagKey = "nemur:order:pager:tag"

        // min scroll distance before toggling toolbar
        private const val MIN_SCROLL_DISTANCE_Y = 10f

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